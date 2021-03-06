/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elan.internal;

import com.google.common.base.Optional;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.clustering.CandidateAlreadyRegisteredException;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.interfacemanager.exceptions.InterfaceAlreadyExistsException;
import org.opendaylight.genius.interfacemanager.globals.IfmConstants;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchRegister;
import org.opendaylight.genius.utils.ServiceIndex;
import org.opendaylight.genius.utils.clustering.EntityOwnerUtils;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.infrautils.inject.AbstractLifecycle;
import org.opendaylight.netvirt.elan.statusanddiag.ElanStatusMonitor;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.elanmanager.api.ElanHelper;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.elanmanager.exceptions.MacNotFoundException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeInterface.EtreeInterfaceType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan._interface.forwarding.entries.ElanInterfaceMac;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.elan._interface.StaticMacEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.elan._interface.StaticMacEntriesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.state.Elan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntry;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanServiceProvider extends AbstractLifecycle implements IElanService {

    private static final Logger LOG = LoggerFactory.getLogger(ElanServiceProvider.class);

    private final IdManagerService idManager;
    private final IInterfaceManager interfaceManager;
    private final ElanInstanceManager elanInstanceManager;
    private final ElanBridgeManager bridgeMgr;
    private final DataBroker broker;
    private final ElanStatusMonitor elanStatusMonitor;
    private final ElanUtils elanUtils;

    private boolean generateIntBridgeMac = true;
    private boolean isL2BeforeL3;

    @Inject
    public ElanServiceProvider(IdManagerService idManager, IInterfaceManager interfaceManager,
                               ElanInstanceManager elanInstanceManager, ElanBridgeManager bridgeMgr,
                               DataBroker dataBroker,
                               ElanInterfaceManager elanInterfaceManager,
                               ElanStatusMonitor elanStatusMonitor, ElanUtils elanUtils,
                               EntityOwnershipService entityOwnershipService) {
        this.idManager = idManager;
        this.interfaceManager = interfaceManager;
        this.elanInstanceManager = elanInstanceManager;
        this.bridgeMgr = bridgeMgr;
        this.broker = dataBroker;
        this.elanStatusMonitor = elanStatusMonitor;
        this.elanUtils = elanUtils;
        elanInterfaceManager.setElanUtils(elanUtils);
        try {
            EntityOwnerUtils.registerEntityCandidateForOwnerShip(entityOwnershipService,
                    HwvtepSouthboundConstants.ELAN_ENTITY_TYPE, HwvtepSouthboundConstants.ELAN_ENTITY_TYPE,
                    null/*listener*/);
        } catch (CandidateAlreadyRegisteredException e) {
            LOG.error("failed to register the entity");
        }
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void start() throws Exception {
        LOG.info("Starting ElnaServiceProvider");
        elanStatusMonitor.reportStatus("STARTING");
        setIsL2BeforeL3();
        try {
            createIdPool();
            elanStatusMonitor.reportStatus("OPERATIONAL");
        } catch (Exception e) {
            elanStatusMonitor.reportStatus("ERROR");
            throw e;
        }
    }

    @Override
    protected void stop() throws Exception {
    }

    @Override
    public Boolean isOpenStackVniSemanticsEnforced() {
        return elanUtils.isOpenStackVniSemanticsEnforced();
    }

    private void createIdPool() throws Exception {
        CreateIdPoolInput createPool = new CreateIdPoolInputBuilder().setPoolName(ElanConstants.ELAN_ID_POOL_NAME)
                .setLow(ElanConstants.ELAN_ID_LOW_VALUE).setHigh(ElanConstants.ELAN_ID_HIGH_VALUE).build();
        Future<RpcResult<Void>> result = idManager.createIdPool(createPool);
        if (result != null && result.get().isSuccessful()) {
            LOG.debug("ELAN Id Pool is created successfully");
        }
    }

    @Override
    public boolean createElanInstance(String elanInstanceName, long macTimeout, String description) {
        ElanInstance existingElanInstance = elanInstanceManager.getElanInstanceByName(elanInstanceName);
        boolean isSuccess = true;
        if (existingElanInstance != null) {
            if (compareWithExistingElanInstance(existingElanInstance, macTimeout, description)) {
                LOG.debug("Elan Instance is already present in the Operational DS {}", existingElanInstance);
                return true;
            } else {
                ElanInstance updateElanInstance = new ElanInstanceBuilder().setElanInstanceName(elanInstanceName)
                        .setDescription(description).setMacTimeout(macTimeout)
                        .setKey(new ElanInstanceKey(elanInstanceName)).build();
                MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION,
                        ElanHelper.getElanInstanceConfigurationDataPath(elanInstanceName), updateElanInstance);
                LOG.debug("Updating the Elan Instance {} with MAC TIME-OUT %l and Description %s ",
                        updateElanInstance, macTimeout, description);
            }
        } else {
            ElanInstance elanInstance = new ElanInstanceBuilder().setElanInstanceName(elanInstanceName)
                    .setMacTimeout(macTimeout).setDescription(description).setKey(new ElanInstanceKey(elanInstanceName))
                    .build();
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION,
                    ElanHelper.getElanInstanceConfigurationDataPath(elanInstanceName), elanInstance);
            LOG.debug("Creating the new Elan Instance {}", elanInstance);
        }
        return isSuccess;
    }

    @Override
    public boolean createEtreeInstance(String elanInstanceName, long macTimeout, String description) {
        ElanInstance existingElanInstance = elanInstanceManager.getElanInstanceByName(elanInstanceName);
        boolean isSuccess = true;
        if (existingElanInstance != null) {
            if (compareWithExistingElanInstance(existingElanInstance, macTimeout, description)) {
                LOG.warn("Etree Instance is already present in the Operational DS {}", existingElanInstance);
                return true;
            } else {
                EtreeInstance etreeInstance = new EtreeInstanceBuilder().build();
                ElanInstance updateElanInstance = new ElanInstanceBuilder().setElanInstanceName(elanInstanceName)
                        .setDescription(description).setMacTimeout(macTimeout)
                        .setKey(new ElanInstanceKey(elanInstanceName))
                        .addAugmentation(EtreeInstance.class, etreeInstance).build();
                MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION,
                        ElanHelper.getElanInstanceConfigurationDataPath(elanInstanceName), updateElanInstance);
                LOG.debug("Updating the Etree Instance {} with MAC TIME-OUT %l and Description %s ",
                        updateElanInstance, macTimeout, description);
            }
        } else {
            EtreeInstance etreeInstance = new EtreeInstanceBuilder().build();
            ElanInstance elanInstance = new ElanInstanceBuilder().setElanInstanceName(elanInstanceName)
                    .setMacTimeout(macTimeout).setDescription(description).setKey(new ElanInstanceKey(elanInstanceName))
                    .addAugmentation(EtreeInstance.class, etreeInstance).build();
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION,
                    ElanHelper.getElanInstanceConfigurationDataPath(elanInstanceName), elanInstance);
            LOG.debug("Creating the new Etree Instance {}", elanInstance);
        }
        return isSuccess;
    }

    @Override
    public EtreeInterface getEtreeInterfaceByElanInterfaceName(String elanInterface) {
        return ElanUtils.getEtreeInterfaceByElanInterfaceName(broker, elanInterface);
    }

    public static boolean compareWithExistingElanInstance(ElanInstance existingElanInstance, long macTimeOut,
            String description) {
        boolean isEqual = false;
        if (existingElanInstance.getMacTimeout() == macTimeOut
                && existingElanInstance.getDescription().equals(description)) {
            isEqual = true;
        }
        return isEqual;
    }

    @Override
    public void updateElanInstance(String elanInstanceName, long newMacTimout, String newDescription) {
        createElanInstance(elanInstanceName, newMacTimout, newDescription);
    }

    @Override
    public boolean deleteEtreeInstance(String etreeInstanceName) {
        return deleteElanInstance(etreeInstanceName);
    }

    @Override
    public boolean deleteElanInstance(String elanInstanceName) {
        boolean isSuccess = false;
        ElanInstance existingElanInstance = elanInstanceManager.getElanInstanceByName(elanInstanceName);
        if (existingElanInstance == null) {
            LOG.debug("Elan Instance is not present {}", existingElanInstance);
            return isSuccess;
        }
        LOG.debug("Deletion of the existing Elan Instance {}", existingElanInstance);
        ElanUtils.delete(broker, LogicalDatastoreType.CONFIGURATION,
                ElanHelper.getElanInstanceConfigurationDataPath(elanInstanceName));
        isSuccess = true;
        return isSuccess;
    }

    @Override
    public void addEtreeInterface(String etreeInstanceName, String interfaceName, EtreeInterfaceType interfaceType,
            List<String> staticMacAddresses, String description) {
        ElanInstance existingElanInstance = elanInstanceManager.getElanInstanceByName(etreeInstanceName);
        if (existingElanInstance != null && existingElanInstance.getAugmentation(EtreeInstance.class) != null) {
            EtreeInterface etreeInterface = new EtreeInterfaceBuilder().setEtreeInterfaceType(interfaceType).build();
            ElanInterface elanInterface;
            if (staticMacAddresses == null) {
                elanInterface = new ElanInterfaceBuilder().setElanInstanceName(etreeInstanceName)
                        .setDescription(description).setName(interfaceName).setKey(new ElanInterfaceKey(interfaceName))
                        .addAugmentation(EtreeInterface.class, etreeInterface).build();
            } else {
                List<StaticMacEntries> staticMacEntries = ElanUtils.getStaticMacEntries(staticMacAddresses);
                elanInterface = new ElanInterfaceBuilder().setElanInstanceName(etreeInstanceName)
                        .setDescription(description).setName(interfaceName)
                        .setStaticMacEntries(staticMacEntries)
                        .setKey(new ElanInterfaceKey(interfaceName))
                        .addAugmentation(EtreeInterface.class, etreeInterface).build();
            }
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION,
                    ElanUtils.getElanInterfaceConfigurationDataPathId(interfaceName), elanInterface);
            LOG.debug("Creating the new Etree Interface {}", elanInterface);
        }
    }

    @Override
    public void addElanInterface(String elanInstanceName, String interfaceName, List<String> staticMacAddresses,
            String description) {
        ElanInstance existingElanInstance = elanInstanceManager.getElanInstanceByName(elanInstanceName);
        if (existingElanInstance != null) {
            ElanInterfaceBuilder elanInterfaceBuilder = new ElanInterfaceBuilder()
                    .setElanInstanceName(elanInstanceName)
                    .setDescription(description).setName(interfaceName)
                    .setKey(new ElanInterfaceKey(interfaceName));
            if (staticMacAddresses != null) {
                List<StaticMacEntries> staticMacEntries = ElanUtils.getStaticMacEntries(staticMacAddresses);
                elanInterfaceBuilder.setStaticMacEntries(staticMacEntries);
            }
            ElanInterface elanInterface = elanInterfaceBuilder.build();
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION,
                    ElanUtils.getElanInterfaceConfigurationDataPathId(interfaceName), elanInterface);
            LOG.debug("Created the new ELan Interface {}", elanInterface);
        }
    }

    @Override
    public void updateElanInterface(String elanInstanceName, String interfaceName,
            List<String> updatedStaticMacAddresses, String newDescription) {
        ElanInterface existingElanInterface = ElanUtils.getElanInterfaceByElanInterfaceName(broker, interfaceName);
        if (existingElanInterface == null) {
            return;
        }

        List<StaticMacEntries> updatedStaticMacEntries = ElanUtils.getStaticMacEntries(updatedStaticMacAddresses);
        LOG.debug("updating the ElanInterface with new Mac Entries {}", updatedStaticMacAddresses);
        ElanInterface elanInterface = new ElanInterfaceBuilder().setElanInstanceName(elanInstanceName)
                .setName(interfaceName).setDescription(newDescription).setStaticMacEntries(updatedStaticMacEntries)
                .setKey(new ElanInterfaceKey(interfaceName)).build();
        MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION,
                ElanUtils.getElanInterfaceConfigurationDataPathId(interfaceName), elanInterface);
    }

    @Override
    public void deleteEtreeInterface(String elanInstanceName, String interfaceName) {
        deleteElanInterface(elanInstanceName, interfaceName);
        LOG.debug("deleting the Etree Interface {}", interfaceName);
    }

    @Override
    public void deleteElanInterface(String elanInstanceName, String interfaceName) {
        ElanInterface existingElanInterface = ElanUtils.getElanInterfaceByElanInterfaceName(broker, interfaceName);
        if (existingElanInterface != null) {
            ElanUtils.delete(broker, LogicalDatastoreType.CONFIGURATION,
                    ElanUtils.getElanInterfaceConfigurationDataPathId(interfaceName));
            LOG.debug("deleting the Elan Interface {}", existingElanInterface);
        }
    }

    @Override
    public void addStaticMacAddress(String elanInstanceName, String interfaceName, String macAddress) {
        ElanInterface existingElanInterface = ElanUtils.getElanInterfaceByElanInterfaceName(broker, interfaceName);
        PhysAddress updateStaticMacAddress = new PhysAddress(macAddress);
        if (existingElanInterface != null) {
            StaticMacEntriesBuilder staticMacEntriesBuilder = new StaticMacEntriesBuilder();
            StaticMacEntries staticMacEntry = staticMacEntriesBuilder.setMacAddress(updateStaticMacAddress).build();
            InstanceIdentifier<StaticMacEntries> staticMacEntriesIdentifier =
                    ElanUtils.getStaticMacEntriesCfgDataPathIdentifier(interfaceName,
                    macAddress);
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, staticMacEntriesIdentifier, staticMacEntry);
            return;
        }

        return;
    }

    @Override
    public void deleteStaticMacAddress(String elanInstanceName, String interfaceName, String macAddress)
            throws MacNotFoundException {
        ElanInterface existingElanInterface = ElanUtils.getElanInterfaceByElanInterfaceName(broker, interfaceName);
        if (existingElanInterface != null) {
            InstanceIdentifier<StaticMacEntries> staticMacEntriesIdentifier =
                    ElanUtils.getStaticMacEntriesCfgDataPathIdentifier(interfaceName,
                    macAddress);
            MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, staticMacEntriesIdentifier);
        }
    }

    @Override
    public Collection<MacEntry> getElanMacTable(String elanInstanceName) {
        Elan elanInfo = ElanUtils.getElanByName(broker, elanInstanceName);
        List<MacEntry> macAddress = new ArrayList<>();
        if (elanInfo == null) {
            return macAddress;
        }
        List<String> elanInterfaces = elanInfo.getElanInterfaces();
        if (elanInterfaces != null && elanInterfaces.size() > 0) {
            for (String elanInterface : elanInterfaces) {
                ElanInterfaceMac elanInterfaceMac = elanUtils.getElanInterfaceMacByInterfaceName(elanInterface);
                if (elanInterfaceMac != null && elanInterfaceMac.getMacEntry() != null
                        && elanInterfaceMac.getMacEntry().size() > 0) {
                    macAddress.addAll(elanInterfaceMac.getMacEntry());
                }
            }
        }
        return macAddress;
    }

    @Override
    public void flushMACTable(String elanInstanceName) {
        Elan elanInfo = ElanUtils.getElanByName(broker, elanInstanceName);
        if (elanInfo == null) {
            return;
        }
        List<String> elanInterfaces = elanInfo.getElanInterfaces();
        if (elanInterfaces == null || elanInterfaces.isEmpty()) {
            return;
        }
        for (String elanInterface : elanInterfaces) {
            ElanInterfaceMac elanInterfaceMac = elanUtils.getElanInterfaceMacByInterfaceName(elanInterface);
            if (elanInterfaceMac.getMacEntry() != null && elanInterfaceMac.getMacEntry().size() > 0) {
                List<MacEntry> macEntries = elanInterfaceMac.getMacEntry();
                for (MacEntry macEntry : macEntries) {
                    try {
                        deleteStaticMacAddress(elanInstanceName, elanInterface, macEntry.getMacAddress().getValue());
                    } catch (MacNotFoundException e) {
                        LOG.error("Mac Not Found Exception {}", e);
                    }
                }
            }
        }

    }

    @Override
    public ElanInstance getElanInstance(String elanName) {
        return ElanUtils.getElanInstanceByName(broker, elanName);
    }

    @Override
    public List<ElanInstance> getElanInstances() {
        InstanceIdentifier<ElanInstances> elanInstancesIdentifier = InstanceIdentifier.builder(ElanInstances.class)
                .build();
        return elanUtils.read(broker, LogicalDatastoreType.CONFIGURATION, elanInstancesIdentifier).transform(
                ElanInstances::getElanInstance).or(Collections.emptyList());
    }

    @Override
    public List<String> getElanInterfaces(String elanInstanceName) {
        List<String> elanInterfaces = new ArrayList<>();
        InstanceIdentifier<ElanInterfaces> elanInterfacesIdentifier = InstanceIdentifier.builder(ElanInterfaces.class)
                .build();
        Optional<ElanInterfaces> elanInterfacesOptional = elanUtils.read(broker, LogicalDatastoreType.CONFIGURATION,
                elanInterfacesIdentifier);
        if (!elanInterfacesOptional.isPresent()) {
            return elanInterfaces;
        }
        List<ElanInterface> elanInterfaceList = elanInterfacesOptional.get().getElanInterface();
        for (ElanInterface elanInterface : elanInterfaceList) {
            if (elanInterface.getElanInstanceName().equals(elanInstanceName)) {
                elanInterfaces.add(elanInterface.getName());
            }
        }
        return elanInterfaces;
    }

    public boolean getGenerateIntBridgeMac() {
        return generateIntBridgeMac;
    }

    public void setGenerateIntBridgeMac(boolean generateIntBridgeMac) {
        this.generateIntBridgeMac = generateIntBridgeMac;
    }

    @Override
    public void createExternalElanNetworks(Node node) {
        handleExternalElanNetworks(node, true, (elanInstance, interfaceName) -> {
            createExternalElanNetwork(elanInstance, interfaceName);
            return null;
        });
    }

    @Override
    public void createExternalElanNetwork(ElanInstance elanInstance) {
        handleExternalElanNetwork(elanInstance, false, (elanInstance1, interfaceName) -> {
            createExternalElanNetwork(elanInstance1, interfaceName);
            return null;
        });
    }

    protected void createExternalElanNetwork(ElanInstance elanInstance, BigInteger dpId) {
        String providerIntfName = bridgeMgr.getProviderInterfaceName(dpId, elanInstance.getPhysicalNetworkName());
        String intfName = providerIntfName + IfmConstants.OF_URI_SEPARATOR + elanInstance.getSegmentationId();
        Interface memberIntf = interfaceManager.getInterfaceInfoFromConfigDataStore(intfName);
        if (memberIntf == null) {
            LOG.debug("creating vlan prv intf in elan {}, dpn {}", elanInstance.getElanInstanceName(),
                    dpId);
            createExternalElanNetwork(elanInstance, providerIntfName);
        }
    }

    private void createExternalElanNetwork(ElanInstance elanInstance, String interfaceName) {
        if (interfaceName == null) {
            LOG.trace("No physical interface is attached to {}", elanInstance.getPhysicalNetworkName());
            return;
        }

        String elanInterfaceName = createIetfInterfaces(elanInstance, interfaceName);
        addElanInterface(elanInstance.getElanInstanceName(), elanInterfaceName, null, null);
    }

    @Override
    public void updateExternalElanNetwork(ElanInstance elanInstance) {
        handleExternalElanNetwork(elanInstance, true, (elanInstance1, interfaceName) -> {
            createExternalElanNetwork(elanInstance1, interfaceName);
            return null;
        });
    }

    @Override
    public void deleteExternalElanNetworks(Node node) {
        handleExternalElanNetworks(node, false, (elanInstance, interfaceName) -> {
            deleteExternalElanNetwork(elanInstance, interfaceName);
            return null;
        });
    }

    @Override
    public void deleteExternalElanNetwork(ElanInstance elanInstance) {
        handleExternalElanNetwork(elanInstance, false, (elanInstance1, interfaceName) -> {
            deleteExternalElanNetwork(elanInstance1, interfaceName);
            return null;
        });
    }

    protected void deleteExternalElanNetwork(ElanInstance elanInstance, BigInteger dpnId) {
        String providerIntfName = bridgeMgr.getProviderInterfaceName(dpnId, elanInstance.getPhysicalNetworkName());
        String intfName = providerIntfName + IfmConstants.OF_URI_SEPARATOR + elanInstance.getSegmentationId();
        Interface memberIntf = interfaceManager.getInterfaceInfoFromConfigDataStore(intfName);
        if (memberIntf != null) {
            deleteElanInterface(elanInstance.getElanInstanceName(), intfName);
            deleteIetfInterface(intfName);
            LOG.debug("delete vlan prv intf {} in elan {}, dpID {}", intfName,
                    elanInstance.getElanInstanceName(), dpnId);
        } else {
            LOG.debug("vlan prv intf {} not found in interfacemgr config DS", intfName);
        }
    }

    private void deleteExternalElanNetwork(ElanInstance elanInstance, String interfaceName) {
        if (interfaceName == null) {
            LOG.trace("No physial interface is attached to {}", elanInstance.getPhysicalNetworkName());
            return;
        }

        String elanInstanceName = elanInstance.getElanInstanceName();
        for (String elanInterface : getExternalElanInterfaces(elanInstanceName)) {
            if (elanInterface.startsWith(interfaceName)) {
                if (ElanUtils.isVlan(elanInstance)) {
                    deleteIetfInterface(elanInterface);
                }
                String trunkInterfaceName = getTrunkInterfaceName(interfaceName);
                if (shouldDeleteTrunk(trunkInterfaceName, elanInterface)) {
                    deleteIetfInterface(trunkInterfaceName);
                }
                deleteElanInterface(elanInstanceName, elanInterface);
            }
        }
    }

    private boolean shouldDeleteTrunk(String trunkInterfaceName, String elanInterfaceName) {
        List<Interface> childInterfaces = interfaceManager.getChildInterfaces(trunkInterfaceName);
        if (childInterfaces == null || childInterfaces.isEmpty()
                || (childInterfaces.size() == 1 && elanInterfaceName.equals(childInterfaces.get(0).getName()))) {
            LOG.debug("No more VLAN member interfaces left for trunk {}", trunkInterfaceName);
            return true;
        }

        LOG.debug("Trunk interface {} has {} VLAN member interfaces left", trunkInterfaceName, childInterfaces.size());
        return false;
    }

    @Override
    public void updateExternalElanNetworks(Node origNode, Node updatedNode) {
        if (!bridgeMgr.isIntegrationBridge(updatedNode)) {
            return;
        }

        List<ElanInstance> elanInstances = getElanInstances();
        if (elanInstances == null || elanInstances.isEmpty()) {
            LOG.trace("No ELAN instances found");
            return;
        }

        LOG.debug("updateExternalElanNetworks, orig bridge {} . updated bridge {}", origNode, updatedNode);

        Map<String, String> origProviderMappping = getMapFromOtherConfig(origNode,
                ElanBridgeManager.PROVIDER_MAPPINGS_KEY);
        Map<String, String> updatedProviderMappping = getMapFromOtherConfig(updatedNode,
                ElanBridgeManager.PROVIDER_MAPPINGS_KEY);

        boolean hasDatapathIdOnOrigNode = bridgeMgr.hasDatapathID(origNode);
        boolean hasDatapathIdOnUpdatedNode = bridgeMgr.hasDatapathID(updatedNode);
        BigInteger origDpnID = bridgeMgr.getDatapathId(origNode);

        for (ElanInstance elanInstance : elanInstances) {
            String physicalNetworkName = elanInstance.getPhysicalNetworkName();
            boolean createExternalElanNw = true;
            if (physicalNetworkName != null) {
                String origPortName = origProviderMappping.get(physicalNetworkName);
                String updatedPortName = updatedProviderMappping.get(physicalNetworkName);
                /**
                 * for internal vlan network, vlan provider interface creation should be
                 * triggered only if there is existing vlan provider intf indicating presence
                 * of VM ports on the DPN
                 */
                if (hasDatapathIdOnOrigNode && !elanInstance.isExternal()
                        && ElanUtils.isVlan(elanInstance)) {
                    String externalIntf = getExternalElanInterface(elanInstance.getElanInstanceName(),
                            origDpnID);
                    if (externalIntf == null) {
                        createExternalElanNw = false;
                    }
                }
                if (hasPortNameRemoved(origPortName, updatedPortName)) {
                    deleteExternalElanNetwork(elanInstance,
                            bridgeMgr.getProviderInterfaceName(origNode, physicalNetworkName));
                }

                if (createExternalElanNw && (hasPortNameUpdated(origPortName, updatedPortName)
                        || hasDatapathIdAdded(hasDatapathIdOnOrigNode, hasDatapathIdOnUpdatedNode))) {
                    createExternalElanNetwork(elanInstance,
                            bridgeMgr.getProviderInterfaceName(updatedNode, physicalNetworkName));
                }
            }
        }
    }

    private boolean hasDatapathIdAdded(boolean hasDatapathIdOnOrigNode, boolean hasDatapathIdOnUpdatedNode) {
        return !hasDatapathIdOnOrigNode && hasDatapathIdOnUpdatedNode;
    }

    private boolean hasPortNameUpdated(String origPortName, String updatedPortName) {
        return updatedPortName != null && !updatedPortName.equals(origPortName);
    }

    private boolean hasPortNameRemoved(String origPortName, String updatedPortName) {
        return origPortName != null && !origPortName.equals(updatedPortName);
    }

    private Map<String, String> getMapFromOtherConfig(Node node, String otherConfigColumn) {
        return bridgeMgr.getOpenvswitchOtherConfigMap(node, otherConfigColumn);
    }

    @Override
    public Collection<String> getExternalElanInterfaces(String elanInstanceName) {
        List<String> elanInterfaces = getElanInterfaces(elanInstanceName);
        if (elanInterfaces == null || elanInterfaces.isEmpty()) {
            LOG.trace("No ELAN interfaces defined for {}", elanInstanceName);
            return Collections.emptySet();
        }

        Set<String> externalElanInterfaces = new HashSet<>();
        for (String elanInterface : elanInterfaces) {
            if (interfaceManager.isExternalInterface(elanInterface)) {
                externalElanInterfaces.add(elanInterface);
            }
        }

        return externalElanInterfaces;
    }

    @Override
    public String getExternalElanInterface(String elanInstanceName, BigInteger dpnId) {
        return elanUtils.getExternalElanInterface(elanInstanceName, dpnId);
    }

    @Override
    public boolean isExternalInterface(String interfaceName) {
        return interfaceManager.isExternalInterface(interfaceName);
    }

    @Override
    public ElanInterface getElanInterfaceByElanInterfaceName(String interfaceName) {
        return ElanUtils.getElanInterfaceByElanInterfaceName(broker, interfaceName);
    }

    @Override
    public void handleKnownL3DmacAddress(String macAddress, String elanInstanceName, int addOrRemove) {
        if (!isL2BeforeL3) {
            LOG.trace("ELAN service is after L3VPN in the Netvirt pipeline skip known L3DMAC flows installation");
            return;
        }
        ElanInstance elanInstance = ElanUtils.getElanInstanceByName(broker, elanInstanceName);
        if (elanInstance == null) {
            LOG.warn("Null elan instance {}", elanInstanceName);
            return;
        }

        List<BigInteger> dpnsIdsForElanInstance = elanUtils.getParticipatingDpnsInElanInstance(elanInstanceName);
        if (dpnsIdsForElanInstance == null || dpnsIdsForElanInstance.isEmpty()) {
            LOG.warn("No DPNs for elan instance {}", elanInstance);
            return;
        }

        elanUtils.handleDmacRedirectToDispatcherFlows(elanInstance.getElanTag(), elanInstanceName, macAddress,
                addOrRemove, dpnsIdsForElanInstance);
    }

    @Override
    public List<MatchInfoBase> getEgressMatchesForElanInstance(String elanInstanceName) {
        ElanInstance elanInstance = getElanInstance(elanInstanceName);
        if (elanInstance == null) {
            LOG.debug("No ELAN instance found for {}", elanInstanceName);
            return Collections.emptyList();
        }

        Long elanTag = elanInstance.getElanTag();
        if (elanTag == null) {
            LOG.debug("No ELAN tag found for {}", elanInstanceName);
            return Collections.emptyList();
        }
        return Collections.singletonList(
                new NxMatchRegister(ElanConstants.ELAN_REG_ID, elanTag, MetaDataUtil.getElanMaskForReg()));
    }

    /**
     * Create ietf-interfaces based on the ELAN segment type.<br>
     * For segment type flat - create transparent interface pointing to the
     * patch-port attached to the physnet port.<br>
     * For segment type vlan - create trunk interface pointing to the patch-port
     * attached to the physnet port + trunk-member interface pointing to the
     * trunk interface.
     *
     * @param elanInstance
     *            ELAN instance
     * @param parentRef
     *            parent interface name
     * @return the name of the interface to be added to the ELAN instance i.e.
     *         trunk-member name for vlan network and transparent for flat
     *         network or null otherwise
     */
    private String createIetfInterfaces(ElanInstance elanInstance, String parentRef) {
        String interfaceName = null;

        try {
            String trunkName = getTrunkInterfaceName(parentRef);
            // trunk interface may have been created by other vlan network
            Interface trunkInterface = interfaceManager.getInterfaceInfoFromConfigDataStore(trunkName);
            if (trunkInterface == null) {
                interfaceManager.createVLANInterface(trunkName, parentRef, null, null, null,
                        IfL2vlan.L2vlanMode.Trunk, true);
            }
            if (ElanUtils.isFlat(elanInstance)) {
                interfaceName = trunkName;
            } else if (ElanUtils.isVlan(elanInstance)) {
                Long segmentationId = elanInstance.getSegmentationId();
                interfaceName = parentRef + IfmConstants.OF_URI_SEPARATOR + segmentationId;
                interfaceManager.createVLANInterface(interfaceName, trunkName, null, segmentationId.intValue(), null,
                        IfL2vlan.L2vlanMode.TrunkMember, true);
            }
        } catch (InterfaceAlreadyExistsException e) {
            LOG.trace("Interface {} was already created", interfaceName);
        }

        return interfaceName;
    }

    private void deleteIetfInterface(String interfaceName) {
        InterfaceKey interfaceKey = new InterfaceKey(interfaceName);
        InstanceIdentifier<Interface> interfaceInstanceIdentifier = InstanceIdentifier.builder(Interfaces.class)
                .child(Interface.class, interfaceKey).build();
        MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, interfaceInstanceIdentifier);
        LOG.debug("Deleting IETF interface {}", interfaceName);
    }

    private void handleExternalElanNetworks(Node node, boolean skipIntVlanNw,
                                            BiFunction<ElanInstance, String, Void> function) {
        if (!bridgeMgr.isIntegrationBridge(node)) {
            return;
        }

        List<ElanInstance> elanInstances = getElanInstances();
        if (elanInstances == null || elanInstances.isEmpty()) {
            LOG.trace("No ELAN instances found");
            return;
        }

        for (ElanInstance elanInstance : elanInstances) {
            if (skipIntVlanNw && !elanInstance.isExternal() && ElanUtils.isVlan(elanInstance)) {
                continue;
            }
            String interfaceName = bridgeMgr.getProviderInterfaceName(node, elanInstance.getPhysicalNetworkName());
            if (interfaceName != null) {
                function.apply(elanInstance, interfaceName);
            }
        }
    }

    private void handleExternalElanNetwork(ElanInstance elanInstance, boolean update,
                                           BiFunction<ElanInstance, String, Void> function) {
        String elanInstanceName = elanInstance.getElanInstanceName();
        if (elanInstance.getPhysicalNetworkName() == null) {
            LOG.trace("No physical network attached to {}", elanInstanceName);
            return;
        }

        List<Node> nodes = bridgeMgr.southboundUtils.getOvsdbNodes();
        if (nodes == null || nodes.isEmpty()) {
            LOG.trace("No OVS nodes found while creating external network for ELAN {}",
                    elanInstance.getElanInstanceName());
            return;
        }

        for (Node node : nodes) {
            if (bridgeMgr.isIntegrationBridge(node)) {
                if (update && !elanInstance.isExternal()) {
                    DpnInterfaces dpnInterfaces = elanUtils.getElanInterfaceInfoByElanDpn(elanInstanceName,
                            bridgeMgr.getDatapathId(node));
                    if (dpnInterfaces == null || dpnInterfaces.getInterfaces().size() == 0) {
                        continue;
                    }
                }
                String interfaceName = bridgeMgr.getProviderInterfaceName(node, elanInstance.getPhysicalNetworkName());
                function.apply(elanInstance, interfaceName);
            }
        }
    }

    private String getTrunkInterfaceName(String parentRef) {
        return parentRef + IfmConstants.OF_URI_SEPARATOR + "trunk";
    }

    private void setIsL2BeforeL3() {
        short elanServiceRealIndex = ServiceIndex.getIndex(NwConstants.ELAN_SERVICE_NAME,
                NwConstants.ELAN_SERVICE_INDEX);
        short l3vpnServiceRealIndex = ServiceIndex.getIndex(NwConstants.L3VPN_SERVICE_NAME,
                NwConstants.L3VPN_SERVICE_INDEX);
        if (elanServiceRealIndex < l3vpnServiceRealIndex) {
            LOG.info("ELAN service is set before L3VPN service in the Netvirt pipeline");
            isL2BeforeL3 = true;
        } else {
            LOG.info("ELAN service is set after L3VPN service in the Netvirt pipeline");
            isL2BeforeL3 = false;
        }
    }
}
