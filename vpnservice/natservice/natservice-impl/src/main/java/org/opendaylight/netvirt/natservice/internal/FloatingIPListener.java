/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import com.google.common.base.Optional;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.actions.ActionSetDestinationIp;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldEthernetSource;
import org.opendaylight.genius.mdsalutil.actions.ActionSetSourceIp;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.instructions.InstructionWriteMetadata;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetDestination;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv4Destination;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv4Source;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.FloatingIpInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProviderTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.NetworksKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.RouterPorts;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.PortsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.PortsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.ports.InternalToExternalPortMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.ports.InternalToExternalPortMapBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.ports.InternalToExternalPortMapKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FloatingIPListener extends AsyncDataTreeChangeListenerBase<InternalToExternalPortMap, FloatingIPListener>
        implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(FloatingIPListener.class);
    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalManager;
    private final OdlInterfaceRpcService interfaceManager;
    private final FloatingIPHandler floatingIPHandler;

    @Inject
    public FloatingIPListener(final DataBroker dataBroker, final IMdsalApiManager mdsalManager,
                              final OdlInterfaceRpcService interfaceManager,
                              final FloatingIPHandler floatingIPHandler) {
        super(InternalToExternalPortMap.class, FloatingIPListener.class);
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
        this.interfaceManager = interfaceManager;
        this.floatingIPHandler = floatingIPHandler;
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<InternalToExternalPortMap> getWildCardPath() {
        return InstanceIdentifier.create(FloatingIpInfo.class).child(RouterPorts.class).child(Ports.class)
                .child(InternalToExternalPortMap.class);
    }

    @Override
    protected FloatingIPListener getDataTreeChangeListener() {
        return FloatingIPListener.this;
    }

    @Override
    protected void add(final InstanceIdentifier<InternalToExternalPortMap> identifier,
                       final InternalToExternalPortMap mapping) {
        LOG.trace("FloatingIPListener add ip mapping method - key: " + identifier + ", value=" + mapping);
        processFloatingIPAdd(identifier, mapping);
    }

    @Override
    protected void remove(InstanceIdentifier<InternalToExternalPortMap> identifier, InternalToExternalPortMap mapping) {
        LOG.trace("FloatingIPListener remove ip mapping method - key: " + identifier + ", value=" + mapping);
        processFloatingIPDel(identifier, mapping);
    }

    @Override
    protected void update(InstanceIdentifier<InternalToExternalPortMap> identifier, InternalToExternalPortMap
            original, InternalToExternalPortMap update) {
        LOG.trace("FloatingIPListener update ip mapping method - key: {}, original: {}, update: {}",
            identifier, original, update);
    }

    private FlowEntity buildPreDNATFlowEntity(BigInteger dpId, InternalToExternalPortMap mapping, long routerId, long
            associatedVpn) {
        String externalIp = mapping.getExternalIp();
        Uuid floatingIpId = mapping.getExternalId();
        //Get the FIP MAC address for DNAT
        String floatingIpPortMacAddress = NatUtil.getFloatingIpPortMacFromFloatingIpId(dataBroker, floatingIpId);
        if (floatingIpPortMacAddress == null) {
            LOG.error("NAT Service : Unable to retrieve floatingIpPortMacAddress from floating IP UUID {} "
                    + "for floating IP {}", floatingIpId, externalIp);
            return null;
        }
        LOG.info("NAT Service : Bulding DNAT Flow entity for ip {} ", externalIp);
        long segmentId = (associatedVpn == NatConstants.INVALID_ID) ? routerId : associatedVpn;
        LOG.debug("NAT Service : Segment id {} in build preDNAT Flow", segmentId);

        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);

        matches.add(new MatchIpv4Destination(externalIp, "32"));
        //Match Destination Floating IP MAC Address on table = 25 (PDNAT_TABLE)
        matches.add(new MatchEthernetDestination(new MacAddress(floatingIpPortMacAddress)));

//        matches.add(new MatchMetadata(
//                BigInteger.valueOf(vpnId), MetaDataUtil.METADATA_MASK_VRFID));
        List<ActionInfo> actionsInfos = new ArrayList<>();
        String internalIp = mapping.getInternalIp();
        actionsInfos.add(new ActionSetDestinationIp(internalIp, "32"));

        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionWriteMetadata(MetaDataUtil.getVpnIdMetadata(segmentId),
                MetaDataUtil.METADATA_MASK_VRFID));
        instructions.add(new InstructionApplyActions(actionsInfos));
        instructions.add(new InstructionGotoTable(NwConstants.DNAT_TABLE));

        String flowRef = NatUtil.getFlowRef(dpId, NwConstants.PDNAT_TABLE, routerId, externalIp);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.PDNAT_TABLE, flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NwConstants.COOKIE_DNAT_TABLE, matches, instructions);

        return flowEntity;
    }

    private FlowEntity buildDNATFlowEntity(BigInteger dpId, InternalToExternalPortMap mapping, long routerId, long
            associatedVpn) {
        String externalIp = mapping.getExternalIp();
        LOG.info("NAT Service : Bulding DNAT Flow entity for ip {} ", externalIp);

        long segmentId = (associatedVpn == NatConstants.INVALID_ID) ? routerId : associatedVpn;
        LOG.debug("NAT Service : Segment id {} in build DNAT", segmentId);

        List<MatchInfo> matches = new ArrayList<>();
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(segmentId), MetaDataUtil.METADATA_MASK_VRFID));

        matches.add(MatchEthernetType.IPV4);
        String internalIp = mapping.getInternalIp();
        matches.add(new MatchIpv4Destination(internalIp, "32"));

        List<ActionInfo> actionsInfos = new ArrayList<>();
//        actionsInfos.add(new ActionSetDestinationIp(internalIp, "32"));

        List<InstructionInfo> instructions = new ArrayList<>();
//        instructions.add(new InstructionWriteMetadata(BigInteger.valueOf
//                (routerId), MetaDataUtil.METADATA_MASK_VRFID));
        actionsInfos.add(new ActionNxResubmit(NwConstants.L3_FIB_TABLE));
        instructions.add(new InstructionApplyActions(actionsInfos));
        //instructions.add(new InstructionGotoTable(NatConstants.L3_FIB_TABLE));

        String flowRef = NatUtil.getFlowRef(dpId, NwConstants.DNAT_TABLE, routerId, internalIp);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.DNAT_TABLE, flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NwConstants.COOKIE_DNAT_TABLE, matches, instructions);

        return flowEntity;

    }

    private FlowEntity buildPreSNATFlowEntity(BigInteger dpId, String internalIp, String externalIp, long vpnId, long
            routerId, long associatedVpn) {

        LOG.info("NAT Service : Building PSNAT Flow entity for ip {} ", internalIp);

        long segmentId = (associatedVpn == NatConstants.INVALID_ID) ? routerId : associatedVpn;

        LOG.debug("NAT Service : Segment id {} in build preSNAT flow", segmentId);

        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);

        matches.add(new MatchIpv4Source(internalIp, "32"));

        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(segmentId), MetaDataUtil.METADATA_MASK_VRFID));

        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionSetSourceIp(externalIp, "32"));

        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(
                new InstructionWriteMetadata(MetaDataUtil.getVpnIdMetadata(vpnId), MetaDataUtil.METADATA_MASK_VRFID));
        instructions.add(new InstructionApplyActions(actionsInfos));
        instructions.add(new InstructionGotoTable(NwConstants.SNAT_TABLE));

        String flowRef = NatUtil.getFlowRef(dpId, NwConstants.PSNAT_TABLE, routerId, internalIp);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.PSNAT_TABLE, flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NwConstants.COOKIE_DNAT_TABLE, matches, instructions);

        return flowEntity;
    }

    private FlowEntity buildSNATFlowEntity(BigInteger dpId, InternalToExternalPortMap mapping, long vpnId, Uuid
            externalNetworkId) {
        String internalIp = mapping.getInternalIp();
        LOG.info("Building SNAT Flow entity for ip {} ", internalIp);

        ProviderTypes provType = NatUtil.getProviderTypefromNetworkId(dataBroker, externalNetworkId);
        if (provType == null) {
            LOG.error("NAT Service : Unable to get Network Provider Type for network {}", externalNetworkId);
            return null;
        }

        List<MatchInfo> matches = new ArrayList<>();
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(vpnId), MetaDataUtil.METADATA_MASK_VRFID));
        matches.add(MatchEthernetType.IPV4);
        String externalIp = mapping.getExternalIp();
        matches.add(new MatchIpv4Source(externalIp, "32"));

        List<ActionInfo> actionsInfo = new ArrayList<>();
        Uuid floatingIpId = mapping.getExternalId();
        String macAddress = NatUtil.getFloatingIpPortMacFromFloatingIpId(dataBroker, floatingIpId);
        if (macAddress != null) {
            actionsInfo.add(new ActionSetFieldEthernetSource(new MacAddress(macAddress)));
        } else {
            LOG.warn("No MAC address found for floating IP {}", externalIp);
        }

        LOG.trace("NAT Service : External Network Provider Type is {}, resubmit to FIB", provType.toString());
        actionsInfo.add(new ActionNxResubmit(NwConstants.L3_FIB_TABLE));
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(actionsInfo));
        String flowRef = NatUtil.getFlowRef(dpId, NwConstants.SNAT_TABLE, vpnId, externalIp);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.SNAT_TABLE, flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NwConstants.COOKIE_DNAT_TABLE, matches, instructions);

        return flowEntity;

    }

    private void createDNATTblEntry(BigInteger dpnId, InternalToExternalPortMap mapping, long routerId, long vpnId,
                                    long associatedVpnId) {
        FlowEntity preFlowEntity = buildPreDNATFlowEntity(dpnId, mapping, routerId, associatedVpnId);
        if (preFlowEntity == null) {
            LOG.error("NAT Service : Flow entity received as NULL. Cannot proceed with installation of Pre-DNAT flow "
                    + "table {} --> table {} on DpnId {}", NwConstants.PDNAT_TABLE, NwConstants.DNAT_TABLE, dpnId);
        } else {
            mdsalManager.installFlow(preFlowEntity);
            FlowEntity flowEntity = buildDNATFlowEntity(dpnId, mapping, routerId, associatedVpnId);
            mdsalManager.installFlow(flowEntity);
        }
    }

    private void removeDNATTblEntry(BigInteger dpnId, String internalIp, String externalIp, long routerId) {
        FlowEntity preFlowEntity = buildPreDNATDeleteFlowEntity(dpnId, externalIp, routerId);
        mdsalManager.removeFlow(preFlowEntity);

        FlowEntity flowEntity = buildDNATDeleteFlowEntity(dpnId, internalIp, routerId);
        mdsalManager.removeFlow(flowEntity);
    }

    private void createSNATTblEntry(BigInteger dpnId, InternalToExternalPortMap mapping, long vpnId, long routerId,
                                    long associatedVpnId, Uuid externalNetworkId) {
        FlowEntity preFlowEntity = buildPreSNATFlowEntity(dpnId, mapping.getInternalIp(), mapping.getExternalIp(),
            vpnId, routerId, associatedVpnId);
        mdsalManager.installFlow(preFlowEntity);

        FlowEntity flowEntity = buildSNATFlowEntity(dpnId, mapping, vpnId, externalNetworkId);
        mdsalManager.installFlow(flowEntity);
    }

    private void removeSNATTblEntry(BigInteger dpnId, String internalIp, String externalIp, long routerId, long vpnId) {
        FlowEntity preFlowEntity = buildPreSNATDeleteFlowEntity(dpnId, internalIp, routerId);
        mdsalManager.removeFlow(preFlowEntity);

        FlowEntity flowEntity = buildSNATDeleteFlowEntity(dpnId, externalIp, vpnId);
        mdsalManager.removeFlow(flowEntity);
    }

    private Uuid getExtNetworkId(final InstanceIdentifier<RouterPorts> portIid,
                                 LogicalDatastoreType dataStoreType) {
        Optional<RouterPorts> rtrPort =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        dataStoreType, portIid);
        if (!rtrPort.isPresent()) {
            LOG.error("NAT Service : Unable to read router port entry for {}", portIid);
            return null;
        }

        Uuid extNwId = rtrPort.get().getExternalNetworkId();
        return extNwId;
    }

    private long getVpnId(Uuid extNwId, Uuid floatingIpExternalId) {
        Uuid subnetId = NatUtil.getFloatingIpPortSubnetIdFromFloatingIpId(dataBroker, floatingIpExternalId);
        if (subnetId != null) {
            long vpnId = NatUtil.getVpnId(dataBroker, subnetId.getValue());
            if (vpnId != NatConstants.INVALID_ID) {
                LOG.debug("Got vpnId {} for floatingIpExternalId {}", vpnId, floatingIpExternalId);
                return vpnId;
            }
        }

        InstanceIdentifier<Networks> nwId = InstanceIdentifier.builder(ExternalNetworks.class).child(Networks.class,
                new NetworksKey(extNwId)).build();
        Optional<Networks> nw =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.CONFIGURATION, nwId);
        if (!nw.isPresent()) {
            LOG.error("NAT Service : Unable to read external network for {}", extNwId);
            return NatConstants.INVALID_ID;
        }

        Uuid vpnUuid = nw.get().getVpnid();
        if (vpnUuid == null) {
            return NatConstants.INVALID_ID;
        }

        //Get the id using the VPN UUID (also vpn instance name)
        return NatUtil.readVpnId(dataBroker, vpnUuid.getValue());
    }

    private void processFloatingIPAdd(final InstanceIdentifier<InternalToExternalPortMap> identifier,
                                      final InternalToExternalPortMap mapping) {
        LOG.trace("Add event - key: {}, value: {}", identifier, mapping);

        final String routerId = identifier.firstKeyOf(RouterPorts.class).getRouterId();
        final PortsKey pKey = identifier.firstKeyOf(Ports.class);
        String interfaceName = pKey.getPortName();

        InstanceIdentifier<RouterPorts> portIid = identifier.firstIdentifierOf(RouterPorts.class);
        createNATFlowEntries(interfaceName, mapping, portIid, routerId);
    }

    private void processFloatingIPDel(final InstanceIdentifier<InternalToExternalPortMap> identifier,
                                      final InternalToExternalPortMap mapping) {
        LOG.trace("Del event - key: {}, value: {}", identifier, mapping);

        final String routerId = identifier.firstKeyOf(RouterPorts.class).getRouterId();
        final PortsKey pKey = identifier.firstKeyOf(Ports.class);
        String interfaceName = pKey.getPortName();

        InstanceIdentifier<RouterPorts> portIid = identifier.firstIdentifierOf(RouterPorts.class);
        removeNATFlowEntries(interfaceName, mapping, portIid, routerId, null);
    }

    private InetAddress getInetAddress(String ipAddr) {
        InetAddress ipAddress = null;
        try {
            ipAddress = InetAddress.getByName(ipAddr);
        } catch (UnknownHostException e) {
            LOG.error("NAT Service : UnknowHostException for ip {}", ipAddr);
        }
        return ipAddress;
    }

    private boolean validateIpMapping(InternalToExternalPortMap mapping) {
        return getInetAddress(mapping.getInternalIp()) != null && getInetAddress(mapping.getExternalIp()) != null;
    }

    void createNATFlowEntries(String interfaceName, final InternalToExternalPortMap mapping,
                              final InstanceIdentifier<RouterPorts> portIid, final String routerName) {
        if (!validateIpMapping(mapping)) {
            LOG.warn("NAT Service : Not a valid ip addresses in the mapping {}", mapping);
            return;
        }

        //Get the DPN on which this interface resides
        BigInteger dpnId = NatUtil.getDpnForInterface(interfaceManager, interfaceName);

        if (dpnId.equals(BigInteger.ZERO)) {
            LOG.error("NAT Service : No DPN for interface {}. NAT flow entries for ip mapping {} will "
                + "not be installed", interfaceName, mapping);
            return;
        }

        long routerId = NatUtil.getVpnId(dataBroker, routerName);
        if (routerId == NatConstants.INVALID_ID) {
            LOG.warn("NAT Service : Could not retrieve router id for {} to create NAT Flow entries", routerName);
            return;
        }
        //Check if the router to vpn association is present
        //long associatedVpnId = NatUtil.getAssociatedVpn(dataBroker, routerName);
        Uuid associatedVpn = NatUtil.getVpnForRouter(dataBroker, routerName);
        long associatedVpnId = NatConstants.INVALID_ID;
        if (associatedVpn == null) {
            LOG.debug("NAT Service : Router {} is not assicated with any BGP VPN instance", routerName);
        } else {
            LOG.debug("NAT Service : Router {} is associated with VPN Instance with Id {}", routerName, associatedVpn);
            associatedVpnId = NatUtil.getVpnId(dataBroker, associatedVpn.getValue());
            LOG.debug("NAT Service : vpninstance Id is {} for VPN {}", associatedVpnId, associatedVpn);
            //routerId = associatedVpnId;
        }

        Uuid extNwId = getExtNetworkId(portIid, LogicalDatastoreType.CONFIGURATION);
        if (extNwId == null) {
            LOG.error("NAT Service : External network associated with interface {} could not be retrieved",
                interfaceName);
            LOG.error("NAT Service : NAT flow entries will not be installed {}", mapping);
            return;
        }
        long vpnId = getVpnId(extNwId, mapping.getExternalId());
        if (vpnId < 0) {
            LOG.error("NAT Service : No VPN associated with Ext nw {}. Unable to create SNAT table entry "
                    + "for fixed ip {}", extNwId, mapping.getInternalIp());
            return;
        }

        //Create the DNAT and SNAT table entries
        createDNATTblEntry(dpnId, mapping, routerId, vpnId, associatedVpnId);


        createSNATTblEntry(dpnId, mapping, vpnId, routerId, associatedVpnId, extNwId);

        floatingIPHandler.onAddFloatingIp(dpnId, routerName, extNwId, interfaceName, mapping);
    }

    void createNATFlowEntries(BigInteger dpnId,  String interfaceName, String routerName, Uuid externalNetworkId,
                              InternalToExternalPortMap mapping) {
        String internalIp = mapping.getInternalIp();
        long routerId = NatUtil.getVpnId(dataBroker, routerName);
        if (routerId == NatConstants.INVALID_ID) {
            LOG.warn("NAT Service : Could not retrieve router id for {} to create NAT Flow entries", routerName);
            return;
        }
        //Check if the router to vpn association is present
        long associatedVpnId = NatUtil.getAssociatedVpn(dataBroker, routerName);
        if (associatedVpnId == NatConstants.INVALID_ID) {
            LOG.debug("NAT Service : Router {} is not assicated with any BGP VPN instance", routerName);
        } else {
            LOG.debug("NAT Service : Router {} is associated with VPN Instance with Id {}",
                routerName, associatedVpnId);
            //routerId = associatedVpnId;
        }

        long vpnId = getVpnId(externalNetworkId, mapping.getExternalId());
        if (vpnId < 0) {
            LOG.error("NAT Service : Unable to create SNAT table entry for fixed ip {}", internalIp);
            return;
        }
        //Create the DNAT and SNAT table entries
        createDNATTblEntry(dpnId, mapping, routerId, vpnId, associatedVpnId);

        createSNATTblEntry(dpnId, mapping, vpnId, routerId, associatedVpnId, externalNetworkId);

        floatingIPHandler.onAddFloatingIp(dpnId, routerName, externalNetworkId, interfaceName, mapping);
    }

    void createNATOnlyFlowEntries(BigInteger dpnId, String routerName, String associatedVPN,
                                  Uuid externalNetworkId, InternalToExternalPortMap mapping) {
        String internalIp = mapping.getInternalIp();
        //String segmentId = associatedVPN == null ? routerName : associatedVPN;
        LOG.debug("NAT Service : Retrieving vpn id for VPN {} to proceed with create NAT Flows", routerName);
        long routerId = NatUtil.getVpnId(dataBroker, routerName);
        if (routerId == NatConstants.INVALID_ID) {
            LOG.warn("Could not retrieve vpn id for {} to create NAT Flow entries", routerName);
            return;
        }
        long associatedVpnId = NatUtil.getVpnId(dataBroker, associatedVPN);
        LOG.debug("NAT Service : Associated VPN Id {} for router {}", associatedVpnId, routerName);
        long vpnId = getVpnId(externalNetworkId, mapping.getExternalId());
        if (vpnId < 0) {
            LOG.error("NAT Service : Unable to create SNAT table entry for fixed ip {}", internalIp);
            return;
        }
        //Create the DNAT and SNAT table entries
        FlowEntity preFlowEntity = buildPreDNATFlowEntity(dpnId, mapping, routerId, associatedVpnId);
        mdsalManager.installFlow(preFlowEntity);

        FlowEntity flowEntity = buildDNATFlowEntity(dpnId, mapping, routerId, associatedVpnId);
        mdsalManager.installFlow(flowEntity);

        String externalIp = mapping.getExternalIp();
        preFlowEntity = buildPreSNATFlowEntity(dpnId, internalIp, externalIp, vpnId , routerId, associatedVpnId);
        mdsalManager.installFlow(preFlowEntity);

        flowEntity = buildSNATFlowEntity(dpnId, mapping, vpnId, externalNetworkId);
        mdsalManager.installFlow(flowEntity);

    }

    void removeNATFlowEntries(String interfaceName, final InternalToExternalPortMap mapping,
                              InstanceIdentifier<RouterPorts> portIid, final String routerName, BigInteger dpnId) {
        String internalIp = mapping.getInternalIp();
        String externalIp = mapping.getExternalIp();
        //Get the DPN on which this interface resides
        if (dpnId == null) {
            dpnId = NatUtil.getDpnForInterface(interfaceManager, interfaceName);
            if (dpnId.equals(BigInteger.ZERO)) {
                LOG.info("NAT Service: Abort processing Floating ip configuration. No DPN for port: {}", interfaceName);
                return;
            }
        }

        long routerId = NatUtil.getVpnId(dataBroker, routerName);
        if (routerId == NatConstants.INVALID_ID) {
            LOG.warn("NAT Service : Could not retrieve router id for {} to remove NAT Flow entries", routerName);
            return;
        }

        //Delete the DNAT and SNAT table entries
        removeDNATTblEntry(dpnId, internalIp, externalIp, routerId);

        Uuid extNwId = getExtNetworkId(portIid, LogicalDatastoreType.OPERATIONAL);
        if (extNwId == null) {
            LOG.error("NAT Service : External network associated with interface {} could not be retrieved",
                interfaceName);
            return;
        }
        long vpnId = getVpnId(extNwId, mapping.getExternalId());
        if (vpnId < 0) {
            LOG.error("NAT Service : No VPN associated with ext nw {}. Unable to delete SNAT table "
                + "entry for fixed ip {}", extNwId, internalIp);
            return;
        }
        removeSNATTblEntry(dpnId, internalIp, externalIp, routerId, vpnId);
        ProviderTypes provType = NatEvpnUtil.getExtNwProvTypeFromRouterName(dataBroker, routerName);
        if (provType == null) {
            return;
        }
        if (provType == ProviderTypes.VXLAN) {
            floatingIPHandler.onRemoveFloatingIp(dpnId, routerName, extNwId, mapping, NatConstants.DEFAULT_L3VNI_VALUE);
            removeOperationalDS(routerName, interfaceName, internalIp, externalIp);
            return;
        }
        long label = getOperationalIpMapping(routerName, interfaceName, internalIp);
        if (label < 0) {
            LOG.error("NAT Service : Could not retrieve label for prefix {} in router {}", internalIp, routerId);
            return;
        }
        floatingIPHandler.onRemoveFloatingIp(dpnId, routerName, extNwId, mapping, (int) label);
        removeOperationalDS(routerName, interfaceName, internalIp, externalIp);
    }

    void removeNATFlowEntries(BigInteger dpnId, String interfaceName, String vpnName, String routerName,
                              InternalToExternalPortMap mapping) {
        String internalIp = mapping.getInternalIp();
        String externalIp = mapping.getExternalIp();
        long routerId = NatUtil.getVpnId(dataBroker, routerName);
        if (routerId == NatConstants.INVALID_ID) {
            LOG.warn("NAT Service : Could not retrieve router id for {} to remove NAT Flow entries", routerName);
            return;
        }

        long vpnId = NatUtil.getVpnId(dataBroker, vpnName);
        if (vpnId == NatConstants.INVALID_ID) {
            LOG.warn("NAT Service : VPN Id not found for {} to remove NAT flow entries {}", vpnName, internalIp);
        }

        //Delete the DNAT and SNAT table entries
        removeDNATTblEntry(dpnId, internalIp, externalIp, routerId);

        removeSNATTblEntry(dpnId, internalIp, externalIp, routerId, vpnId);
        ProviderTypes provType = NatEvpnUtil.getExtNwProvTypeFromRouterName(dataBroker, routerName);
        if (provType == null) {
            return;
        }
        if (provType == ProviderTypes.VXLAN) {
            floatingIPHandler.cleanupFibEntries(dpnId, vpnName, externalIp, NatConstants.DEFAULT_L3VNI_VALUE);
            removeOperationalDS(routerName, interfaceName, internalIp, externalIp);
            return;
        }
        long label = getOperationalIpMapping(routerName, interfaceName, internalIp);
        if (label < 0) {
            LOG.error("NAT Service : Could not retrieve label for prefix {} in router {}", internalIp, routerId);
            return;
        }
        floatingIPHandler.cleanupFibEntries(dpnId, vpnName, externalIp, label);
        removeOperationalDS(routerName, interfaceName, internalIp, externalIp);
    }

    protected long getOperationalIpMapping(String routerId, String interfaceName, String internalIp) {
        InstanceIdentifier<InternalToExternalPortMap> intExtPortMapIdentifier =
            NatUtil.getIntExtPortMapIdentifier(routerId, interfaceName, internalIp);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                LogicalDatastoreType.OPERATIONAL, intExtPortMapIdentifier).transform(
                InternalToExternalPortMap::getLabel).or(NatConstants.INVALID_ID);
    }

    static void updateOperationalDS(DataBroker dataBroker, String routerId, String interfaceName, long label,
                                    String internalIp, String externalIp) {

        LOG.info("NAT Service : Updating operational DS for floating ip config : {} with label {}", internalIp, label);
        InstanceIdentifier<Ports> portsId = NatUtil.getPortsIdentifier(routerId, interfaceName);
        Optional<Ports> optPorts =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.OPERATIONAL, portsId);
        InternalToExternalPortMap intExtPortMap = new InternalToExternalPortMapBuilder().setKey(new
                InternalToExternalPortMapKey(internalIp)).setInternalIp(internalIp).setExternalIp(externalIp)
                .setLabel(label).build();
        if (optPorts.isPresent()) {
            LOG.debug("Ports {} entry already present. Updating intExtPortMap for internal ip {}", interfaceName,
                    internalIp);
            MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, portsId.child(InternalToExternalPortMap
                    .class, new InternalToExternalPortMapKey(internalIp)), intExtPortMap);
        } else {
            LOG.debug("Adding Ports entry {} along with intExtPortMap {}", interfaceName, internalIp);
            List<InternalToExternalPortMap> intExtPortMapList = new ArrayList<>();
            intExtPortMapList.add(intExtPortMap);
            Ports ports = new PortsBuilder().setKey(new PortsKey(interfaceName)).setPortName(interfaceName)
                    .setInternalToExternalPortMap(intExtPortMapList).build();
            MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, portsId, ports);
        }
    }

    void removeOperationalDS(String routerId, String interfaceName, String internalIp, String externalIp) {
        LOG.info("Remove operational DS for floating ip config: {}", internalIp);
        InstanceIdentifier<InternalToExternalPortMap> intExtPortMapId = NatUtil.getIntExtPortMapIdentifier(routerId,
                interfaceName, internalIp);
        MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL, intExtPortMapId);
    }

    private FlowEntity buildPreDNATDeleteFlowEntity(BigInteger dpId, String externalIp, long routerId) {

        LOG.info("NAT Service : Bulding Delete DNAT Flow entity for ip {} ", externalIp);

        String flowRef = NatUtil.getFlowRef(dpId, NwConstants.PDNAT_TABLE, routerId, externalIp);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.PDNAT_TABLE, flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NwConstants.COOKIE_DNAT_TABLE, null, null);

        return flowEntity;
    }



    private FlowEntity buildDNATDeleteFlowEntity(BigInteger dpId, String internalIp, long routerId) {

        LOG.info("NAT Service : Bulding Delete DNAT Flow entity for ip {} ", internalIp);

        String flowRef = NatUtil.getFlowRef(dpId, NwConstants.DNAT_TABLE, routerId, internalIp);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.DNAT_TABLE, flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NwConstants.COOKIE_DNAT_TABLE, null, null);

        return flowEntity;

    }

    private FlowEntity buildPreSNATDeleteFlowEntity(BigInteger dpId, String internalIp, long routerId) {

        LOG.info("NAT Service : Building Delete PSNAT Flow entity for ip {} ", internalIp);

        String flowRef = NatUtil.getFlowRef(dpId, NwConstants.PSNAT_TABLE, routerId, internalIp);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.PSNAT_TABLE, flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NwConstants.COOKIE_DNAT_TABLE, null, null);
        return flowEntity;
    }

    private FlowEntity buildSNATDeleteFlowEntity(BigInteger dpId, String externalIp, long routerId) {

        LOG.info("NAT Service : Building Delete SNAT Flow entity for ip {} ", externalIp);

        String flowRef = NatUtil.getFlowRef(dpId, NwConstants.SNAT_TABLE, routerId, externalIp);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.SNAT_TABLE, flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NwConstants.COOKIE_DNAT_TABLE, null, null);

        return flowEntity;
    }
}

