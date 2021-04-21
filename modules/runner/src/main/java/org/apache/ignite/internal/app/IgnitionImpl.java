/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.ignite.app.Ignite;
import org.apache.ignite.app.Ignition;
import org.apache.ignite.internal.baseline.BaselineManager;
import org.apache.ignite.configuration.RootKey;
import org.apache.ignite.configuration.internal.ConfigurationManager;
import org.apache.ignite.configuration.storage.ConfigurationType;
import org.apache.ignite.configuration.schemas.network.NetworkConfiguration;
import org.apache.ignite.configuration.schemas.network.NetworkView;
import org.apache.ignite.internal.affinity.AffinityManager;
import org.apache.ignite.configuration.storage.ConfigurationStorage;
import org.apache.ignite.internal.table.distributed.TableManagerImpl;
import org.apache.ignite.internal.vault.VaultManager;
import org.apache.ignite.internal.metastorage.MetaStorageManager;
import org.apache.ignite.lang.IgniteLogger;
import org.apache.ignite.network.ClusterLocalConfiguration;
import org.apache.ignite.network.ClusterService;
import org.apache.ignite.network.message.MessageSerializationRegistry;
import org.apache.ignite.network.scalecube.ScaleCubeClusterServiceFactory;
import org.apache.ignite.internal.raft.Loza;
import org.apache.ignite.internal.storage.DistributedConfigurationStorage;
import org.apache.ignite.internal.storage.LocalConfigurationStorage;
import org.apache.ignite.internal.schema.SchemaManager;
import org.apache.ignite.table.manager.TableManager;
import org.apache.ignite.utils.IgniteProperties;

/**
 * Implementation of an entry point for handling grid lifecycle.
 */
public class IgnitionImpl implements Ignition {
    /** */
    private static final String[] BANNER = new String[] {
        "",
        "           #              ___                         __",
        "         ###             /   |   ____   ____ _ _____ / /_   ___",
        "     #  #####           / /| |  / __ \\ / __ `// ___// __ \\ / _ \\",
        "   ###  ######         / ___ | / /_/ // /_/ // /__ / / / // ___/",
        "  #####  #######      /_/  |_|/ .___/ \\__,_/ \\___//_/ /_/ \\___/",
        "  #######  ######            /_/",
        "    ########  ####        ____               _  __           _____",
        "   #  ########  ##       /  _/____ _ ____   (_)/ /_ ___     |__  /",
        "  ####  #######  #       / / / __ `// __ \\ / // __// _ \\     /_ <",
        "   #####  #####        _/ / / /_/ // / / // // /_ / ___/   ___/ /",
        "     ####  ##         /___/ \\__, //_/ /_//_/ \\__/ \\___/   /____/",
        "       ##                  /____/\n"
    };

    /** */
    private static final String VER_KEY = "version";

    /** */
    private static final IgniteLogger LOG = IgniteLogger.forClass(IgnitionImpl.class);

    /** {@inheritDoc} */
    @Override public synchronized Ignite start(String jsonStrBootstrapCfg) {
        ackBanner();

        // Vault Component startup.
        VaultManager vaultMgr = new VaultManager();

        boolean cfgBootstrappedFromPds = vaultMgr.bootstrapped();

        List<RootKey<?, ?>> rootKeys = new ArrayList<>(Collections.singletonList(NetworkConfiguration.KEY));

        List<ConfigurationStorage> configurationStorages =
            new ArrayList<>(Collections.singletonList(new LocalConfigurationStorage(vaultMgr)));

        // Bootstrap local configuration manager.
        ConfigurationManager locConfigurationMgr = new ConfigurationManager(rootKeys, configurationStorages);

        if (!cfgBootstrappedFromPds && jsonStrBootstrapCfg != null)
            try {
                locConfigurationMgr.bootstrap(jsonStrBootstrapCfg);
            }
            catch (Exception e) {
                LOG.warn("Unable to parse user specific configuration, default configuration will be used", e);
            }
        else if (jsonStrBootstrapCfg != null)
            LOG.warn("User specific configuration will be ignored, cause vault was bootstrapped with pds configuration");
        else
            locConfigurationMgr.configurationRegistry().startStorageConfigurations(ConfigurationType.LOCAL);

        NetworkView netConfigurationView =
            locConfigurationMgr.configurationRegistry().getConfiguration(NetworkConfiguration.KEY).value();

        var serializationRegistry = new MessageSerializationRegistry();

        // Network startup.
        ClusterService clusterNetSvc = new ScaleCubeClusterServiceFactory().createClusterService(
            new ClusterLocalConfiguration(
                "Node" + netConfigurationView.port(),
                netConfigurationView.port(),
                Arrays.asList(netConfigurationView.netClusterNodes()),
                serializationRegistry
            )
        );

        // Raft Component startup.
        Loza raftMgr = new Loza(clusterNetSvc);

        // MetaStorage Component startup.
        MetaStorageManager metaStorageMgr = new MetaStorageManager(
            vaultMgr,
            clusterNetSvc,
            raftMgr
        );

        // TODO IGNITE-14578 Bootstrap configuration manager with distributed configuration.
        configurationStorages.add(new DistributedConfigurationStorage(metaStorageMgr));

        // Start configuration manager.
        ConfigurationManager configurationMgr = new ConfigurationManager(rootKeys, configurationStorages);

        // Baseline manager startup.
        BaselineManager baselineMgr = new BaselineManager(configurationMgr, metaStorageMgr, clusterNetSvc);

        // Affinity manager startup.
        new AffinityManager(configurationMgr, metaStorageMgr, baselineMgr);

        SchemaManager schemaMgr = new SchemaManager(configurationMgr);

        // Distributed table manager startup.
        TableManager distributedTblMgr = new TableManagerImpl(
            configurationMgr,
            clusterNetSvc,
            metaStorageMgr,
            schemaMgr,
            raftMgr
        );

        // TODO IGNITE-14579 Start rest manager.

        // Deploy all resisted watches cause all components are ready and have registered their listeners.
        metaStorageMgr.deployWatches();

        clusterNetSvc.start();

        ackSuccessStart();

        return new IgniteImpl(distributedTblMgr);
    }

    /** */
    private static void ackSuccessStart() {
        LOG.info("Apache Ignite started successfully!");
    }

    /** */
    private static void ackBanner() {
        String ver = IgniteProperties.get(VER_KEY);

        String banner = Arrays
            .stream(BANNER)
            .collect(Collectors.joining("\n"));

        LOG.info(banner + '\n' + " ".repeat(22) + "Apache Ignite ver. " + ver + '\n');
    }
}
