/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
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
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.config.rev170206.NatserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.config.rev170206.NatserviceConfig.NatMode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.Networks;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ExternalNetworkListener extends AsyncDataTreeChangeListenerBase<Networks, ExternalNetworkListener>
    implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ExternalNetworkListener.class);
    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalManager;
    private NatMode natMode = NatMode.Controller;

    @Inject
    public ExternalNetworkListener(final DataBroker dataBroker, final IMdsalApiManager mdsalManager,
            final NatserviceConfig config) {
        super(Networks.class, ExternalNetworkListener.class);
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
        if (config != null) {
            this.natMode = config.getNatMode();
        }
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected ExternalNetworkListener getDataTreeChangeListener() {
        return ExternalNetworkListener.this;
    }

    @Override
    protected InstanceIdentifier<Networks> getWildCardPath() {
        return InstanceIdentifier.create(ExternalNetworks.class).child(Networks.class);
    }

    @Override
    protected void add(final InstanceIdentifier<Networks> identifier,
                       final Networks nw) {
        LOG.trace("NAT Service : External Network add mapping method - key: " + identifier + ", value=" + nw);
        if (natMode == NatMode.Controller) {
            processExternalNwAdd(identifier, nw);
        }
    }

    @Override
    protected void remove(InstanceIdentifier<Networks> identifier, Networks nw) {
        LOG.trace("NAT Service : External Network remove mapping method - key: " + identifier + ", value=" + nw);
        if (natMode == NatMode.Controller) {
            processExternalNwDel(identifier, nw);
        }
    }

    @Override
    protected void update(InstanceIdentifier<Networks> identifier, Networks original, Networks update) {
        LOG.trace("NAT Service : External Network update mapping method - key: {}, original: {}, update: {}",
            identifier, original, update);
        //check if a new router has been added or an already existing router has been deleted from the external
        // nw to router association
        List<Uuid> oldRtrs = original.getRouterIds();
        List<Uuid> newRtrs = update.getRouterIds();
        if (oldRtrs != newRtrs) {
            //handle both addition and removal of routers
            for (Uuid rtr : newRtrs) {
                if (oldRtrs.contains(rtr)) {
                    oldRtrs.remove(rtr);
                } else {
                    if (natMode == NatMode.Conntrack) {
                        return;
                    } else {
                        // new router case
                        //Routers added need to have the corresponding default Fib entry added to the switches in
                        //the router
                        String routerId = rtr.getValue();
                        addOrDelDefFibRouteToSNAT(routerId, true);
                    }

                }
            }

            //Routers removed need to have the corresponding default Fib entry removed from the switches in the router
            for (Uuid rtr : oldRtrs) {
                String routerId = rtr.getValue();
                addOrDelDefFibRouteToSNAT(routerId, false);
            }
        }
    }

    private void processExternalNwAdd(final InstanceIdentifier<Networks> identifier,
                                      final Networks network) {
        LOG.trace("NAT Service : Add event - key: {}, value: {}", identifier, network);
        List<Uuid> routerList = network.getRouterIds();

        if (routerList == null) {
            LOG.debug("No routers associated with external network {}", identifier);
            return;
        }

        for (Uuid router: routerList) {
            String routerId = router.getValue();
            addOrDelDefFibRouteToSNAT(routerId, true);
        }
    }

    private void processExternalNwDel(final InstanceIdentifier<Networks> identifier,
                                      final Networks network) {
        LOG.trace("NAT Service : Add event - key: {}, value: {}", identifier, network);
        List<Uuid> routerList = network.getRouterIds();

        for (Uuid router: routerList) {
            String routerId = router.getValue();
            addOrDelDefFibRouteToSNAT(routerId, false);
        }
    }

    private void addOrDelDefFibRouteToSNAT(String routerId, boolean create) {
        //Router ID is used as the internal VPN's name, hence the vrf-id in VpnInstance Op DataStore
        InstanceIdentifier<VpnInstanceOpDataEntry> id = NatUtil.getVpnInstanceOpDataIdentifier(routerId);
        Optional<VpnInstanceOpDataEntry> vpnInstOp =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.OPERATIONAL, id);
        if (vpnInstOp.isPresent()) {
            List<VpnToDpnList> dpnListInVpn = vpnInstOp.get().getVpnToDpnList();
            if (dpnListInVpn != null) {
                for (VpnToDpnList dpn : dpnListInVpn) {
                    BigInteger dpnId = dpn.getDpnId();
                    long vpnId = NatUtil.readVpnId(dataBroker, vpnInstOp.get().getVrfId());
                    if (create) {
                        installDefNATRouteInDPN(dpnId, vpnId);
                    } else {
                        removeDefNATRouteInDPN(dpnId, vpnId);
                    }
                }
            }
        }
    }

    private FlowEntity buildDefNATFlowEntity(BigInteger dpId, long vpnId) {
        InetAddress defaultIP = null;

        try {
            defaultIP = InetAddress.getByName("0.0.0.0");

        } catch (UnknownHostException e) {
            LOG.error("NAT Service : UnknowHostException in buildDefNATFlowEntity. "
                + "Failed to build FIB Table Flow for Default Route to NAT table ");
            return null;
        }

        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);

        //add match for default route "0.0.0.0/0"
        //matches.add(new MatchInfo(MatchFieldType.ipv4_src, new long[] {
        //        NatUtil.getIpAddress(defaultIP.getAddress()), 0 }));

        //add match for vrfid
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(vpnId), MetaDataUtil.METADATA_MASK_VRFID));

        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionGotoTable(NwConstants.PSNAT_TABLE));

        String flowRef = NatUtil.getFlowRef(dpId, NwConstants.L3_FIB_TABLE, defaultIP, vpnId);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.L3_FIB_TABLE, flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NwConstants.COOKIE_DNAT_TABLE, matches, instructions);

        return flowEntity;


    }

    private void installDefNATRouteInDPN(BigInteger dpnId, long vpnId) {
        FlowEntity flowEntity = buildDefNATFlowEntity(dpnId, vpnId);
        if (flowEntity == null) {
            LOG.error("NAT Service : Flow entity received is NULL. "
                + "Cannot proceed with installation of Default NAT flow");
            return;
        }
        LOG.debug("NAT Service : Installing flow {}", flowEntity);
        NatUtil.djcFlow(flowEntity, NwConstants.ADD_FLOW, mdsalManager);
    }

    private void removeDefNATRouteInDPN(BigInteger dpnId, long vpnId) {
        FlowEntity flowEntity = buildDefNATFlowEntity(dpnId, vpnId);
        if (flowEntity == null) {
            LOG.error("NAT Service : Flow entity received is NULL. "
                + "Cannot proceed with installation of Default NAT flow");
            return;
        }
        LOG.debug("NAT Service : Removing flow {}", flowEntity);
        NatUtil.djcFlow(flowEntity, NwConstants.DEL_FLOW, mdsalManager);
    }
}
