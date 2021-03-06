/*
 * Copyright (c) 2016 - 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.NaptSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProtocolTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.RouterPorts;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.ports.InternalToExternalPortMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.port.map.ip.port.mapping.intext.ip.protocol.type.ip.port.map.IpPortExternal;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitchKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.GetFixedIPsForNeutronPortInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.GetFixedIPsForNeutronPortOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NeutronvpnService;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class InterfaceStateEventListener
    extends AsyncDataTreeChangeListenerBase<Interface, InterfaceStateEventListener>
    implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(InterfaceStateEventListener.class);
    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalManager;
    private final FloatingIPListener floatingIPListener;
    private final NaptManager naptManager;
    private final NeutronvpnService neutronVpnService;
    private static final String NAT_FLOW = "NATFLOW";

    @Inject
    public InterfaceStateEventListener(final DataBroker dataBroker, final IMdsalApiManager mdsalManager,
                                       final FloatingIPListener floatingIPListener,
                                       final NaptManager naptManager,
                                       final NeutronvpnService neutronvpnService) {
        super(Interface.class, InterfaceStateEventListener.class);
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
        this.floatingIPListener = floatingIPListener;
        this.naptManager = naptManager;
        this.neutronVpnService = neutronvpnService;
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("NAT Service : {} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(InterfacesState.class).child(Interface.class);
    }

    @Override
    protected InterfaceStateEventListener getDataTreeChangeListener() {
        return InterfaceStateEventListener.this;
    }

    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void remove(InstanceIdentifier<Interface> identifier, Interface delintrf) {
        LOG.trace("NAT Service : Interface {} removed event received", delintrf);
        if (!L2vlan.class.equals(delintrf.getType())) {
            LOG.debug("NAT Service : Interface {} is a not type Vlan.Ignoring", delintrf.getName());
            return;
        }
        DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
        NatFlowRemoveWorker natFlowRemoveWorker =
                new NatFlowRemoveWorker(delintrf);
        coordinator.enqueueJob(NAT_FLOW + "-" + delintrf.getName(), natFlowRemoveWorker);
    }

    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void update(InstanceIdentifier<Interface> identifier, Interface original, Interface update) {
        LOG.trace("NAT Service : Operation Interface update event - Old: {}, New: {}", original, update);
        if (!L2vlan.class.equals(update.getType())) {
            LOG.debug("NAT Service : Interface {} is not type Vlan.Ignoring", update.getName());
            return;
        }
        DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
        NatFlowUpdateWorker natFlowUpdateWorker =
                new NatFlowUpdateWorker(original, update);
        coordinator.enqueueJob(NAT_FLOW + "-" + update.getName(), natFlowUpdateWorker);

    }

    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void add(InstanceIdentifier<Interface> identifier, Interface intrf) {
        LOG.trace("NAT Service : Interface {} up event received", intrf);
        if (!L2vlan.class.equals(intrf.getType())) {
            LOG.debug("NAT Service : Interface {} is not type vlan.Ignoring", intrf.getName());
            return;
        }
        DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
        NatFlowAddWorker natFlowAddWorker =
                new NatFlowAddWorker(intrf);
        coordinator.enqueueJob(NAT_FLOW + "-" + intrf.getName(), natFlowAddWorker);
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void removeSnatEntriesForPort(String interfaceName, String routerName) {
        Long routerId = NatUtil.getVpnId(dataBroker, routerName);
        if (routerId == NatConstants.INVALID_ID) {
            LOG.error("NAT Service : routerId not found for routername {}", routerName);
            return;
        }
        BigInteger naptSwitch = getNaptSwitchforRouter(dataBroker, routerName);
        if (naptSwitch == null || naptSwitch.equals(BigInteger.ZERO)) {
            LOG.error("NAT Service : NaptSwitch is not elected for router {} with Id {}", routerName, routerId);
            return;
        }
        //getInternalIp for port
        List<String> fixedIps = getFixedIpsForPort(interfaceName);
        if (fixedIps == null) {
            LOG.debug("NAT Service : Internal Ips not found for InterfaceName {} in router {} with id {}",
                interfaceName, routerName, routerId);
            return;
        }
        List<ProtocolTypes> protocolTypesList = getPortocolList();
        for (String internalIp : fixedIps) {
            LOG.debug("NAT Service : Internal Ip retrieved for interface {} is {} in router with Id {}",
                interfaceName, internalIp, routerId);
            for (ProtocolTypes protocol : protocolTypesList) {
                List<Integer> portList = NatUtil.getInternalIpPortListInfo(dataBroker, routerId, internalIp, protocol);
                for (Integer portnum : portList) {
                    //build and remove the flow in outbound table
                    try {
                        removeNatFlow(naptSwitch, NwConstants.OUTBOUND_NAPT_TABLE, routerId, internalIp, portnum);
                    } catch (Exception ex) {
                        LOG.error("NAT Service : Failed to remove snat flow for internalIP {} with "
                                + "Port {} protocol {} for routerId {} in OUTBOUNDTABLE of NaptSwitch {}: {}",
                            internalIp, portnum, protocol, routerId, naptSwitch, ex);
                    }
                    //Get the external IP address and the port from the model
                    NAPTEntryEvent.Protocol proto = protocol.toString().equals(ProtocolTypes.TCP.toString())
                        ? NAPTEntryEvent.Protocol.TCP : NAPTEntryEvent.Protocol.UDP;
                    IpPortExternal ipPortExternal = NatUtil.getExternalIpPortMap(dataBroker, routerId,
                        internalIp, String.valueOf(portnum), proto);
                    if (ipPortExternal == null) {
                        LOG.error("NAT Service : Mapping for internalIp {} with port {} is not found in "
                            + "router with Id {}", internalIp, portnum, routerId);
                        return;
                    }
                    String externalIpAddress = ipPortExternal.getIpAddress();
                    Integer portNumber = ipPortExternal.getPortNum();

                    //build and remove the flow in inboundtable
                    try {
                        removeNatFlow(naptSwitch, NwConstants.INBOUND_NAPT_TABLE, routerId,
                            externalIpAddress, portNumber);
                    } catch (Exception ex) {
                        LOG.error("NAT Service : Failed to remove snat flow internalIP {} with "
                                + "Port {} protocol {} for routerId {} in INBOUNDTABLE of naptSwitch {} : {}",
                            externalIpAddress, portNumber, protocol, routerId, naptSwitch, ex);
                    }

                    String internalIpPort = internalIp + ":" + portnum;
                    // delete the entry from IntExtIpPortMap DS
                    try {
                        naptManager.removeFromIpPortMapDS(routerId, internalIpPort, proto);
                        naptManager.removePortFromPool(internalIpPort, externalIpAddress);
                    } catch (Exception ex) {
                        LOG.error("NAPT Service: releaseIpExtPortMapping failed, Removal of "
                            + "ipportmap {} for router {} failed {}", internalIpPort, routerId, ex);
                    }
                }
                // delete the entry from SnatIntIpPortMap DS
                LOG.debug("NAT Service : Removing InternalIp :{} portlist :{} for protocol :{} of router {}",
                    internalIp, portList, protocol, routerId);
                naptManager.removeFromSnatIpPortDS(routerId, internalIp);
            }
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private String getRouterIdForPort(DataBroker dataBroker, String interfaceName) {
        String vpnName = null;
        String routerName = null;
        if (NatUtil.isVpnInterfaceConfigured(dataBroker, interfaceName)) {
            //getVpnInterface
            VpnInterface vpnInterface = null;
            try {
                vpnInterface = NatUtil.getConfiguredVpnInterface(dataBroker, interfaceName);
            } catch (Exception ex) {
                LOG.error("NAT Service : Unable to process for interface {} as it is not configured", interfaceName);
            }
            if (vpnInterface != null) {
                //getVpnName
                try {
                    vpnName = vpnInterface.getVpnInstanceName();
                    LOG.debug("NAT Service : Retrieved VpnName {}", vpnName);
                } catch (Exception e) {
                    LOG.error("NAT Service : Unable to get vpnname for vpninterface {} - {}", vpnInterface, e);
                }
                if (vpnName != null) {
                    try {
                        routerName = NatUtil.getRouterIdfromVpnInstance(dataBroker, vpnName);
                    } catch (Exception e) {
                        LOG.error("NAT Service : Unable to get routerId for vpnName {} - {}", vpnName, e);
                    }
                    if (routerName != null) {
                        //check router is associated to external network
                        if (NatUtil.isSnatEnabledForRouterId(dataBroker, routerName)) {
                            LOG.debug("NAT Service : Retreived Router Id {} for vpnname {} associated to interface {}",
                                routerName, vpnName, interfaceName);
                            return routerName;
                        } else {
                            LOG.info("NAT Service : Interface {} associated to routerId {} is not "
                                + "associated to external network", interfaceName, routerName);
                        }
                    } else {
                        LOG.debug("NAT Service : Router is not associated to vpnname {} for interface {}",
                            vpnName, interfaceName);
                    }
                } else {
                    LOG.debug("NAT Service : vpnName not found for vpnInterface {} of port {}",
                        vpnInterface, interfaceName);
                }
            }
        } else {
            LOG.debug("NAT Service : Interface {} is not a vpninterface", interfaceName);
        }
        return null;
    }

    private List<ProtocolTypes> getPortocolList() {
        List<ProtocolTypes> protocollist = new ArrayList<>();
        protocollist.add(ProtocolTypes.TCP);
        protocollist.add(ProtocolTypes.UDP);
        return protocollist;
    }

    private BigInteger getNaptSwitchforRouter(DataBroker broker, String routerName) {
        InstanceIdentifier<RouterToNaptSwitch> rtrNaptSw = InstanceIdentifier.builder(NaptSwitches.class)
            .child(RouterToNaptSwitch.class, new RouterToNaptSwitchKey(routerName)).build();
        Optional<RouterToNaptSwitch> routerToNaptSwitchData =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                        LogicalDatastoreType.CONFIGURATION, rtrNaptSw);
        if (routerToNaptSwitchData.isPresent()) {
            RouterToNaptSwitch routerToNaptSwitchInstance = routerToNaptSwitchData.get();
            return routerToNaptSwitchInstance.getPrimarySwitchId();
        }
        return null;
    }

    private void removeNatFlow(BigInteger dpnId, short tableId, Long routerId, String ipAddress, int ipPort) {

        String switchFlowRef = NatUtil.getNaptFlowRef(dpnId, tableId, String.valueOf(routerId), ipAddress, ipPort);
        FlowEntity snatFlowEntity = NatUtil.buildFlowEntity(dpnId, tableId, switchFlowRef);

        mdsalManager.removeFlow(snatFlowEntity);
        LOG.debug("NAT Service : Removed the flow in table {} for the switch with the DPN ID {} for "
            + "router {} ip {} port {}", tableId, dpnId, routerId, ipAddress, ipPort);
    }

    private void processInterfaceAdded(String portName, String routerId) {
        LOG.trace("NAT Service : Processing Interface Add Event for interface {}", portName);
        List<InternalToExternalPortMap> intExtPortMapList = getIntExtPortMapListForPortName(portName, routerId);
        if (intExtPortMapList == null || intExtPortMapList.isEmpty()) {
            LOG.trace("NAT Service : Ip Mapping list is empty/null for portname {}", portName);
            return;
        }
        InstanceIdentifier<RouterPorts> portIid = NatUtil.buildRouterPortsIdentifier(routerId);
        for (InternalToExternalPortMap intExtPortMap : intExtPortMapList) {
            floatingIPListener.createNATFlowEntries(portName, intExtPortMap, portIid, routerId);
        }
    }

    private void processInterfaceRemoved(String portName, BigInteger dpnId, String routerId) {
        LOG.trace("NAT Service: Processing Interface Removed Event for interface {} on DPN ID {}", portName, dpnId);
        List<InternalToExternalPortMap> intExtPortMapList = getIntExtPortMapListForPortName(portName, routerId);
        if (intExtPortMapList == null || intExtPortMapList.isEmpty()) {
            LOG.trace("NAT Service : Ip Mapping list is empty/null for portName {}", portName);
            return;
        }
        InstanceIdentifier<RouterPorts> portIid = NatUtil.buildRouterPortsIdentifier(routerId);
        for (InternalToExternalPortMap intExtPortMap : intExtPortMapList) {
            LOG.trace("NAT Service : Removing DNAT Flow entries for dpnId {} ", dpnId);
            floatingIPListener.removeNATFlowEntries(portName, intExtPortMap, portIid, routerId, dpnId);
        }
    }

    private List<InternalToExternalPortMap> getIntExtPortMapListForPortName(String portName, String routerId) {
        InstanceIdentifier<Ports> portToIpMapIdentifier = NatUtil.buildPortToIpMapIdentifier(routerId, portName);
        Optional<Ports> port =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.CONFIGURATION, portToIpMapIdentifier);
        if (!port.isPresent()) {
            LOG.error("NAT Service : Unable to read router port entry for router ID {} and port name {}",
                routerId, portName);
            return null;
        }
        return port.get().getInternalToExternalPortMap();
    }

    private List<String> getFixedIpsForPort(String interfname) {
        LOG.debug("NAT Service : getFixedIpsForPort method is called for interface {}", interfname);
        try {
            Future<RpcResult<GetFixedIPsForNeutronPortOutput>> result =
                neutronVpnService.getFixedIPsForNeutronPort(new GetFixedIPsForNeutronPortInputBuilder()
                    .setPortId(new Uuid(interfname)).build());

            RpcResult<GetFixedIPsForNeutronPortOutput> rpcResult = result.get();
            if (!rpcResult.isSuccessful()) {
                LOG.warn("NAT Service : RPC Call to GetFixedIPsForNeutronPortOutput returned with Errors {}",
                    rpcResult.getErrors());
            } else {
                return rpcResult.getResult().getFixedIPs();
            }
        } catch (InterruptedException | ExecutionException | NullPointerException ex) {
            LOG.error("NAT Service : Exception while receiving fixedIps for port {}", interfname);
        }
        return null;
    }

    private class NatFlowAddWorker implements Callable<List<ListenableFuture<Void>>> {
        Interface iface;

        NatFlowAddWorker(Interface iface) {
            this.iface = iface;
        }

        @Override
        @SuppressWarnings("checkstyle:IllegalCatch")
        public List<ListenableFuture<Void>> call() {
            final List<ListenableFuture<Void>> futures = new ArrayList<>();
            LOG.trace("NAT Service : Interface {} up event received", iface);
            String interfaceName = iface.getName();
            try {
                LOG.trace("NAT Service : Port added event received for interface {} ", interfaceName);
                String routerId = getRouterIdForPort(dataBroker, interfaceName);
                if (routerId != null) {
                    processInterfaceAdded(interfaceName, routerId);
                }
            } catch (Exception ex) {
                LOG.error("NAT Service : Exception caught in Interface {} Operational State Up event: {}",
                        interfaceName, ex);
            }
            return futures;
        }
    }

    private class NatFlowRemoveWorker implements Callable<List<ListenableFuture<Void>>> {
        Interface delintrf;

        NatFlowRemoveWorker(Interface delintrf) {
            this.delintrf = delintrf;
        }

        @Override
        @SuppressWarnings("checkstyle:IllegalCatch")
        public List<ListenableFuture<Void>> call() {
            final List<ListenableFuture<Void>> futures = new ArrayList<>();
            final String interfaceName = delintrf.getName();
            LOG.trace("NAT Service : Interface {} removed event received", delintrf);
            try {
                String routerName = getRouterIdForPort(dataBroker, interfaceName);
                if (routerName != null) {
                    LOG.trace("NAT Service : Port removed event received for interface {} ", interfaceName);

                    BigInteger dpId;
                    try {
                        dpId = NatUtil.getDpIdFromInterface(delintrf);
                    } catch (Exception e) {
                        LOG.warn(
                                "NAT Service : Unable to retrieve DPNID from Interface operational data store for"
                                        + " Interface {}. Fetching from VPN Interface op data store. ",
                                interfaceName, e);
                        InstanceIdentifier<VpnInterface> id = NatUtil.getVpnInterfaceIdentifier(interfaceName);
                        Optional<VpnInterface> optVpnInterface =
                                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(
                                        dataBroker, LogicalDatastoreType.OPERATIONAL, id);
                        if (!optVpnInterface.isPresent()) {
                            LOG.debug("NAT Service : Interface {} is not a VPN Interface, ignoring.", interfaceName);
                            return futures;
                        }
                        final VpnInterface vpnInterface = optVpnInterface.get();
                        dpId = vpnInterface.getDpnId();
                    }
                    if (dpId == null || dpId.equals(BigInteger.ZERO)) {
                        LOG.error("NAT Service : Unable to get DPN ID for the Interface {}", interfaceName);
                        return futures;
                    }
                    processInterfaceRemoved(interfaceName, dpId, routerName);
                    removeSnatEntriesForPort(interfaceName, routerName);
                } else {
                    LOG.debug("NAT Service : PORT_REMOVE: Router Id is null either Interface {} is not associated "
                            + "to router or failed to retrieve routerId due to exception", interfaceName);
                }
            } catch (Exception e) {
                LOG.error("NAT Service : Exception caught in Interface {} OperationalStateRemove : {}", interfaceName,
                        e);
            }
            return futures;
        }
    }

    private class NatFlowUpdateWorker implements Callable<List<ListenableFuture<Void>>> {
        Interface original;
        Interface update;

        NatFlowUpdateWorker(Interface original, Interface update) {
            this.original = original;
            this.update = update;
        }

        @Override
        @SuppressWarnings("checkstyle:IllegalCatch")
        public List<ListenableFuture<Void>> call() {
            final List<ListenableFuture<Void>> futures = new ArrayList<>();
            String interfaceName = update.getName();
            if (update.getOperStatus().equals(Interface.OperStatus.Up)) {
                LOG.debug("NAT Service : Port UP event received for interface {} ", interfaceName);
            } else if (update.getOperStatus().equals(Interface.OperStatus.Down)) {
                LOG.debug("NAT Service : Port DOWN event received for interface {} ", interfaceName);
                try {
                    String routerName = getRouterIdForPort(dataBroker, interfaceName);
                    if (routerName != null) {
                        removeSnatEntriesForPort(interfaceName, routerName);
                    } else {
                        LOG.debug(
                                "NAT Service : PORT_DOWN: Router Id is null, either Interface {} is not associated "
                                        + "to router {} or failed to retrieve routerId due to exception",
                                interfaceName, routerName);
                    }
                } catch (Exception ex) {
                    LOG.error("NAT Service : Exception caught in Interface {} OperationalStateDown : {}", interfaceName,
                            ex);
                }
            }
            return futures;
        }
    }
}
