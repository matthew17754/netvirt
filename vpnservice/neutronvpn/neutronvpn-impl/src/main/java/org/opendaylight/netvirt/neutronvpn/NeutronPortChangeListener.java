/*
 * Copyright © 2015 - 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import static org.opendaylight.netvirt.neutronvpn.NeutronvpnUtils.buildfloatingIpIdToPortMappingIdentifier;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronConstants;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlanBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAcl;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAclBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.elan._interface.StaticMacEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.port.info.FloatingIpIdToPortMappingBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.port.info.FloatingIpIdToPortMappingKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.Router;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NeutronPortChangeListener extends AsyncDataTreeChangeListenerBase<Port, NeutronPortChangeListener>
        implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronPortChangeListener.class);
    private final DataBroker dataBroker;
    private final NeutronvpnManager nvpnManager;
    private final NeutronvpnNatManager nvpnNatManager;
    private final NeutronSubnetGwMacResolver gwMacResolver;
    private final IElanService elanService;

    @Inject
    public NeutronPortChangeListener(final DataBroker dataBroker,
                                     final NeutronvpnManager neutronvpnManager,
                                     final NeutronvpnNatManager neutronvpnNatManager,
                                     final NeutronSubnetGwMacResolver gwMacResolver,
                                     final IElanService elanService) {
        super(Port.class, NeutronPortChangeListener.class);
        this.dataBroker = dataBroker;
        nvpnManager = neutronvpnManager;
        nvpnNatManager = neutronvpnNatManager;
        this.gwMacResolver = gwMacResolver;
        this.elanService = elanService;
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Port> getWildCardPath() {
        return InstanceIdentifier.create(Neutron.class).child(Ports.class).child(Port.class);
    }

    @Override
    protected NeutronPortChangeListener getDataTreeChangeListener() {
        return NeutronPortChangeListener.this;
    }


    @Override
    protected void add(InstanceIdentifier<Port> identifier, Port input) {
        String portName = input.getUuid().getValue();
        LOG.trace("Adding Port : key: {}, value={}", identifier, input);
        Network network = NeutronvpnUtils.getNeutronNetwork(dataBroker, input.getNetworkId());
        if (network == null || !NeutronvpnUtils.isNetworkTypeSupported(network)) {
            LOG.warn("neutron vpn received a port add() for a network without a provider extension augmentation "
                            + "or with an unsupported network type for the port {} which is part of network {}",
                    portName, network);
            return;
        }
        NeutronvpnUtils.addToPortCache(input);

        String portStatus = NeutronUtils.PORT_STATUS_DOWN;
        if (!Strings.isNullOrEmpty(input.getDeviceOwner()) && !Strings.isNullOrEmpty(input.getDeviceId())) {
            if (input.getDeviceOwner().equals(NeutronConstants.DEVICE_OWNER_ROUTER_INF)) {
                handleRouterInterfaceAdded(input);
                NeutronUtils.createPortStatus(input.getUuid().getValue(), NeutronUtils.PORT_STATUS_ACTIVE, dataBroker);
                return;
            }
            if (NeutronConstants.DEVICE_OWNER_GATEWAY_INF.equals(input.getDeviceOwner())) {
                handleRouterGatewayUpdated(input);
                portStatus = NeutronUtils.PORT_STATUS_ACTIVE;
            } else if (NeutronConstants.DEVICE_OWNER_FLOATING_IP.equals(input.getDeviceOwner())) {
                handleFloatingIpPortUpdated(null, input);
                portStatus = NeutronUtils.PORT_STATUS_ACTIVE;
            }

            if (input.getFixedIps() != null && !input.getFixedIps().isEmpty()) {
                handleNeutronPortCreated(input);
            }
        }

        NeutronUtils.createPortStatus(input.getUuid().getValue(), portStatus, dataBroker);
    }

    @Override
    protected void remove(InstanceIdentifier<Port> identifier, Port input) {
        LOG.trace("Removing Port : key: {}, value={}", identifier, input);
        Network network = NeutronvpnUtils.getNeutronNetwork(dataBroker, input.getNetworkId());
        if (network == null || !NeutronvpnUtils.isNetworkTypeSupported(network)) {
            String portName = input.getUuid().getValue();
            LOG.warn("neutron vpn received a port remove() for a network without a provider extension augmentation "
                            + "or with an unsupported network type for the port {} which is part of network {}",
                    portName, network);
            return;
        }
        NeutronvpnUtils.removeFromPortCache(input);
        NeutronUtils.deletePortStatus(input.getUuid().getValue(), dataBroker);

        if (!Strings.isNullOrEmpty(input.getDeviceOwner()) && !Strings.isNullOrEmpty(input.getDeviceId())) {
            if (input.getDeviceOwner().equals(NeutronConstants.DEVICE_OWNER_ROUTER_INF)) {
                handleRouterInterfaceRemoved(input);
                /* nothing else to do here */
                return;
            } else if (NeutronConstants.DEVICE_OWNER_GATEWAY_INF.equals(input.getDeviceOwner())
                    || NeutronConstants.DEVICE_OWNER_FLOATING_IP.equals(input.getDeviceOwner())) {
                elanService.handleKnownL3DmacAddress(input.getMacAddress().getValue(), input.getNetworkId().getValue(),
                        NwConstants.DEL_FLOW);
            }
        }
        if (input.getFixedIps() != null && !input.getFixedIps().isEmpty()) {
            handleNeutronPortDeleted(input);
        }
    }

    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void update(InstanceIdentifier<Port> identifier, Port original, Port update) {
        final String portName = update.getUuid().getValue();
        LOG.trace("Updating Port : key: {}, original value={}, update value={}", identifier, original, update);
        Network network = NeutronvpnUtils.getNeutronNetwork(dataBroker, update.getNetworkId());
        if (network == null || !NeutronvpnUtils.isNetworkTypeSupported(network)) {
            LOG.warn("neutron vpn received a port update() for a network without a provider extension augmentation "
                    + "or with an unsupported network type for the port {} which is part of network {}",
                    portName, network);
            return;
        }
        NeutronvpnUtils.addToPortCache(update);

        if ((Strings.isNullOrEmpty(original.getDeviceOwner()) || Strings.isNullOrEmpty(original.getDeviceId())
                || NeutronConstants.FLOATING_IP_DEVICE_ID_PENDING.equalsIgnoreCase(original.getDeviceId()))
                && !Strings.isNullOrEmpty(update.getDeviceOwner()) && !Strings.isNullOrEmpty(update.getDeviceId())) {
            if (update.getDeviceOwner().equals(NeutronConstants.DEVICE_OWNER_ROUTER_INF)) {
                handleRouterInterfaceAdded(update);
                return;
            }
            if (NeutronConstants.DEVICE_OWNER_GATEWAY_INF.equals(update.getDeviceOwner())) {
                handleRouterGatewayUpdated(update);
            } else if (NeutronConstants.DEVICE_OWNER_FLOATING_IP.equals(update.getDeviceOwner())) {
                handleFloatingIpPortUpdated(original, update);
            }

            handleNeutronPortCreated(update);
        } else {
            Set<FixedIps> oldIPs = getFixedIpSet(original.getFixedIps());
            Set<FixedIps> newIPs = getFixedIpSet(update.getFixedIps());
            if (!oldIPs.equals(newIPs)) {
                handleNeutronPortUpdated(original, update);
            }
        }

        // check if port security enabled/disabled as part of port update
        boolean origSecurityEnabled = NeutronvpnUtils.getPortSecurityEnabled(original);
        boolean updatedSecurityEnabled = NeutronvpnUtils.getPortSecurityEnabled(update);

        if (origSecurityEnabled || updatedSecurityEnabled) {
            InstanceIdentifier interfaceIdentifier = NeutronvpnUtils.buildVlanInterfaceIdentifier(portName);
            final DataStoreJobCoordinator portDataStoreCoordinator = DataStoreJobCoordinator.getInstance();
            portDataStoreCoordinator.enqueueJob("PORT- " + portName, () -> {
                WriteTransaction wrtConfigTxn = dataBroker.newWriteOnlyTransaction();
                try {
                    Optional<Interface> optionalInf = NeutronvpnUtils.read(dataBroker, LogicalDatastoreType
                            .CONFIGURATION, interfaceIdentifier);
                    if (optionalInf.isPresent()) {
                        InterfaceBuilder interfaceBuilder = new InterfaceBuilder(optionalInf.get());
                        if (origSecurityEnabled || updatedSecurityEnabled) {
                            InterfaceAcl infAcl = handlePortSecurityUpdated(original, update,
                                    origSecurityEnabled, updatedSecurityEnabled, interfaceBuilder).build();
                            interfaceBuilder.addAugmentation(InterfaceAcl.class, infAcl);
                        }
                        LOG.info("Of-port-interface updation for port {}", portName);
                        // Update OFPort interface for this neutron port
                        wrtConfigTxn.put(LogicalDatastoreType.CONFIGURATION, interfaceIdentifier,
                                interfaceBuilder.build());
                    } else {
                        LOG.error("Interface {} is not present", portName);
                    }
                } catch (Exception e) {
                    LOG.error("Failed to update interface {} due to the exception {}", portName, e);
                }
                List<ListenableFuture<Void>> futures = new ArrayList<>();
                futures.add(wrtConfigTxn.submit());
                return futures;
            });
        }
    }

    private void handleFloatingIpPortUpdated(Port original, Port update) {
        if (((original == null) || (original.getDeviceId().equals(NeutronConstants.FLOATING_IP_DEVICE_ID_PENDING)))
            && !update.getDeviceId().equals(NeutronConstants.FLOATING_IP_DEVICE_ID_PENDING)) {
            // populate floating-ip uuid and floating-ip port attributes (uuid, mac and subnet id for the ONLY
            // fixed IP) to be used by NAT, depopulated in NATService once mac is retrieved in the removal path
            addToFloatingIpPortInfo(new Uuid(update.getDeviceId()), update.getUuid(), update.getFixedIps().get(0)
                    .getSubnetId(), update.getMacAddress().getValue());
            elanService.handleKnownL3DmacAddress(update.getMacAddress().getValue(), update.getNetworkId().getValue(),
                    NwConstants.ADD_FLOW);
        }
    }

    private void handleRouterInterfaceAdded(Port routerPort) {
        if (routerPort.getDeviceId() != null) {
            Uuid routerId = new Uuid(routerPort.getDeviceId());
            Uuid infNetworkId = routerPort.getNetworkId();
            Uuid existingVpnId = NeutronvpnUtils.getVpnForNetwork(dataBroker, infNetworkId);

            elanService.handleKnownL3DmacAddress(routerPort.getMacAddress().getValue(), infNetworkId.getValue(),
                    NwConstants.ADD_FLOW);
            if (existingVpnId == null) {
                for (FixedIps portIP : routerPort.getFixedIps()) {
                    Uuid vpnId = NeutronvpnUtils.getVpnForRouter(dataBroker, routerId, true);
                    if (vpnId == null) {
                        vpnId = routerId;
                    }
                    // NOTE:  Please donot change the order of calls to updateSubnetNodeWithFixedIP
                    // and addSubnetToVpn here
                    String ipValue = String.valueOf(portIP.getIpAddress().getValue());
                    nvpnManager.updateSubnetNodeWithFixedIp(portIP.getSubnetId(), routerId,
                            routerPort.getUuid(), ipValue, routerPort.getMacAddress().getValue());
                    nvpnManager.createVpnInterface(vpnId, routerId, routerPort, null);
                    nvpnManager.addSubnetToVpn(vpnId, portIP.getSubnetId());
                    nvpnNatManager.handleSubnetsForExternalRouter(routerId, dataBroker);
                    WriteTransaction wrtConfigTxn = dataBroker.newWriteOnlyTransaction();
                    String portInterfaceName = createOfPortInterface(routerPort, wrtConfigTxn);
                    createElanInterface(routerPort, portInterfaceName, wrtConfigTxn);
                    wrtConfigTxn.submit();
                    PhysAddress mac = new PhysAddress(routerPort.getMacAddress().getValue());
                    LOG.trace("NeutronPortChangeListener Add Subnet Gateway IP {} MAC {} Interface {} VPN {}",
                            ipValue, routerPort.getMacAddress(),
                            routerPort.getUuid().getValue(), vpnId.getValue());

                }
            } else {
                LOG.error("Neutron network {} corresponding to router interface port {} for neutron router {} already"
                    + " associated to VPN {}", infNetworkId.getValue(), routerPort.getUuid().getValue(),
                    routerId.getValue(), existingVpnId.getValue());
            }
        }
    }

    private void handleRouterInterfaceRemoved(Port routerPort) {
        if (routerPort.getDeviceId() != null) {
            Uuid routerId = new Uuid(routerPort.getDeviceId());
            Uuid infNetworkId = routerPort.getNetworkId();

            elanService.handleKnownL3DmacAddress(routerPort.getMacAddress().getValue(), infNetworkId.getValue(),
                    NwConstants.DEL_FLOW);
            for (FixedIps portIP : routerPort.getFixedIps()) {
                Uuid vpnId = NeutronvpnUtils.getVpnForRouter(dataBroker, routerId, true);
                if (vpnId == null) {
                    vpnId = routerId;
                }
                /* Remove ping responder for router interfaces
                *  A router interface reference in a VPN will have to be removed before the host interface references
                * for that subnet in the VPN are removed. This is to ensure that the FIB Entry of the router interface
                *  is not the last entry to be removed for that subnet in the VPN.
                *  If router interface FIB entry is the last to be removed for a subnet in a VPN , then all the host
                *  interface references in the vpn will already have been cleared, which will cause failures in
                *  cleanup of router interface flows*/
                nvpnManager.deleteVpnInterface(vpnId, routerId, routerPort, null);
                // NOTE:  Please donot change the order of calls to removeSubnetFromVpn and
                // and updateSubnetNodeWithFixedIP
                nvpnManager.removeSubnetFromVpn(vpnId, portIP.getSubnetId());
                nvpnManager.updateSubnetNodeWithFixedIp(portIP.getSubnetId(), null,
                        null, null, null);
                WriteTransaction wrtConfigTxn = dataBroker.newWriteOnlyTransaction();
                deleteElanInterface(routerPort.getUuid().getValue(), wrtConfigTxn);
                deleteOfPortInterface(routerPort, wrtConfigTxn);
                wrtConfigTxn.submit();
                nvpnNatManager.handleSubnetsForExternalRouter(routerId, dataBroker);
                String ipValue = String.valueOf(portIP.getIpAddress().getValue());
                NeutronvpnUtils.removeVpnPortFixedIpToPort(dataBroker, vpnId.getValue(),
                        ipValue, null /*writeTransaction*/);
            }
        }
    }

    private void handleRouterGatewayUpdated(Port routerGwPort) {
        Uuid routerId = new Uuid(routerGwPort.getDeviceId());
        Uuid networkId = routerGwPort.getNetworkId();
        elanService.handleKnownL3DmacAddress(routerGwPort.getMacAddress().getValue(), networkId.getValue(),
                NwConstants.ADD_FLOW);

        Router router = NeutronvpnUtils.getNeutronRouter(dataBroker, routerId);
        if (router == null) {
            LOG.warn("No router found for router GW port {} router id {}", routerGwPort.getUuid(), routerId.getValue());
            return;
        }
        gwMacResolver.sendArpRequestsToExtGateways(router);
    }

    private void handleNeutronPortCreated(final Port port) {
        final String portName = port.getUuid().getValue();
        final Uuid portId = port.getUuid();
        final Uuid subnetId = port.getFixedIps().get(0).getSubnetId();
        final DataStoreJobCoordinator portDataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        portDataStoreCoordinator.enqueueJob("PORT- " + portName, () -> {
            WriteTransaction wrtConfigTxn = dataBroker.newWriteOnlyTransaction();
            List<ListenableFuture<Void>> futures = new ArrayList<>();

            // add direct port to subnetMaps config DS
            if (!NeutronUtils.isPortVnicTypeNormal(port)) {
                nvpnManager.updateSubnetmapNodeWithPorts(subnetId, null, portId);
                LOG.info("Port {} is not a NORMAL VNIC Type port; OF Port interfaces are not created", portName);
                futures.add(wrtConfigTxn.submit());
                return futures;
            }
            LOG.info("Of-port-interface creation for port {}", portName);
            // Create of-port interface for this neutron port
            String portInterfaceName = createOfPortInterface(port, wrtConfigTxn);
            LOG.debug("Creating ELAN Interface for port {}", portName);
            createElanInterface(port, portInterfaceName, wrtConfigTxn);

            Subnetmap subnetMap = nvpnManager.updateSubnetmapNodeWithPorts(subnetId, portId, null);
            Uuid vpnId = (subnetMap != null) ? subnetMap.getVpnId() : null;
            Uuid routerId = (subnetMap != null) ? subnetMap.getRouterId() : null;
            if (vpnId != null) {
                // create vpn-interface on this neutron port
                LOG.debug("Adding VPN Interface for port {}", portName);
                nvpnManager.createVpnInterface(vpnId, routerId, port, wrtConfigTxn);
            }
            futures.add(wrtConfigTxn.submit());
            return futures;
        });
    }

    private void handleNeutronPortDeleted(final Port port) {
        final String portName = port.getUuid().getValue();
        final Uuid portId = port.getUuid();
        final Uuid subnetId = port.getFixedIps().get(0).getSubnetId();
        final DataStoreJobCoordinator portDataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        portDataStoreCoordinator.enqueueJob("PORT- " + portName, () -> {
            WriteTransaction wrtConfigTxn = dataBroker.newWriteOnlyTransaction();
            List<ListenableFuture<Void>> futures = new ArrayList<>();

            // remove direct port from subnetMaps config DS
            if (!NeutronUtils.isPortVnicTypeNormal(port)) {
                nvpnManager.removePortsFromSubnetmapNode(subnetId, null, portId);
                LOG.info("Port {} is not a NORMAL VNIC Type port; OF Port interfaces are not created", portName);
                futures.add(wrtConfigTxn.submit());
                return futures;
            }
            Subnetmap subnetMap = nvpnManager.removePortsFromSubnetmapNode(subnetId, portId, null);
            Uuid vpnId = (subnetMap != null) ? subnetMap.getVpnId() : null;
            Uuid routerId = (subnetMap != null) ? subnetMap.getRouterId() : null;
            if (vpnId != null) {
                // remove vpn-interface for this neutron port
                LOG.debug("removing VPN Interface for port {}", portName);
                nvpnManager.deleteVpnInterface(vpnId, routerId, port, wrtConfigTxn);
            }
            // Remove of-port interface for this neutron port
            // ELAN interface is also implicitly deleted as part of this operation
            LOG.debug("Of-port-interface removal for port {}", portName);
            deleteOfPortInterface(port, wrtConfigTxn);
            //dissociate fixedIP from floatingIP if associated
            nvpnManager.dissociatefixedIPFromFloatingIP(port.getUuid().getValue());
            futures.add(wrtConfigTxn.submit());
            return futures;
        });
    }

    private void handleNeutronPortUpdated(final Port portoriginal, final Port portupdate) {
        if (portoriginal.getFixedIps() == null || portoriginal.getFixedIps().isEmpty()) {
            handleNeutronPortCreated(portupdate);
            return;
        }

        if (portupdate.getFixedIps() == null || portupdate.getFixedIps().isEmpty()) {
            LOG.debug("Ignoring portUpdate (fixed_ip removal) for port {} as this case is handled "
                      + "during subnet deletion event.", portupdate.getUuid().getValue());
            return;
        }

        final DataStoreJobCoordinator portDataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        portDataStoreCoordinator.enqueueJob("PORT- " + portupdate.getUuid().getValue(), () -> {
            WriteTransaction wrtConfigTxn = dataBroker.newWriteOnlyTransaction();
            Uuid vpnIdNew = null;
            final Uuid subnetIdOr = portupdate.getFixedIps().get(0).getSubnetId();
            final Uuid subnetIdUp = portupdate.getFixedIps().get(0).getSubnetId();
            // check if subnet UUID has changed upon change in fixedIP
            final Boolean subnetUpdated = subnetIdUp.equals(subnetIdOr) ? false : true;

            if (subnetUpdated) {
                Subnetmap subnetMapOld = nvpnManager.removePortsFromSubnetmapNode(subnetIdOr, portoriginal
                        .getUuid(), null);
                Uuid vpnIdOld = (subnetMapOld != null) ? subnetMapOld.getVpnId() : null;
                Subnetmap subnetMapNew = nvpnManager.updateSubnetmapNodeWithPorts(subnetIdUp, portupdate
                                .getUuid(), null);
                vpnIdNew = (subnetMapNew != null) ? subnetMapNew.getVpnId() : null;
            }
            if (!subnetUpdated) {
                Subnetmap subnetmap = NeutronvpnUtils.getSubnetmap(dataBroker, subnetIdUp);
                vpnIdNew = subnetmap != null ? subnetmap.getVpnId() : null;
            }
            if (vpnIdNew != null) {
                // remove vpn-interface for this neutron port
                LOG.debug("removing VPN Interface for port {}", portupdate.getUuid().getValue());
                nvpnManager.deleteVpnInterface(vpnIdNew, null, portupdate, wrtConfigTxn);
                // create vpn-interface on this neutron port
                LOG.debug("Adding VPN Interface for port {}", portupdate.getUuid().getValue());
                nvpnManager.createVpnInterface(vpnIdNew, null, portupdate, wrtConfigTxn);
            }
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            futures.add(wrtConfigTxn.submit());
            return futures;
        });
    }

    private static InterfaceAclBuilder handlePortSecurityUpdated(Port portOriginal, Port portUpdated, boolean
            origSecurityEnabled, boolean updatedSecurityEnabled, InterfaceBuilder interfaceBuilder) {
        String interfaceName = portUpdated.getUuid().getValue();
        InterfaceAclBuilder interfaceAclBuilder = null;
        if (origSecurityEnabled != updatedSecurityEnabled) {
            interfaceAclBuilder = new InterfaceAclBuilder();
            interfaceAclBuilder.setPortSecurityEnabled(updatedSecurityEnabled);
            if (updatedSecurityEnabled) {
                // Handle security group enabled
                NeutronvpnUtils.populateInterfaceAclBuilder(interfaceAclBuilder, portUpdated);
            } else {
                // Handle security group disabled
                interfaceAclBuilder.setSecurityGroups(new ArrayList<>());
                interfaceAclBuilder.setAllowedAddressPairs(new ArrayList<>());
            }
        } else {
            if (updatedSecurityEnabled) {
                // handle SG add/delete delta
                InterfaceAcl interfaceAcl = interfaceBuilder.getAugmentation(InterfaceAcl.class);
                interfaceAclBuilder = new InterfaceAclBuilder(interfaceAcl);
                interfaceAclBuilder.setSecurityGroups(
                        NeutronvpnUtils.getUpdatedSecurityGroups(interfaceAcl.getSecurityGroups(),
                                portOriginal.getSecurityGroups(), portUpdated.getSecurityGroups()));
                List<AllowedAddressPairs> updatedAddressPairs = NeutronvpnUtils.getUpdatedAllowedAddressPairs(
                        interfaceAcl.getAllowedAddressPairs(), portOriginal.getAllowedAddressPairs(),
                        portUpdated.getAllowedAddressPairs());
                interfaceAclBuilder.setAllowedAddressPairs(NeutronvpnUtils.getAllowedAddressPairsForFixedIps(
                        updatedAddressPairs, portOriginal.getMacAddress(), portOriginal.getFixedIps(),
                        portUpdated.getFixedIps()));
            }
        }
        return interfaceAclBuilder;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private String createOfPortInterface(Port port, WriteTransaction wrtConfigTxn) {
        Interface inf = createInterface(port);
        String infName = inf.getName();

        LOG.debug("Creating OFPort Interface {}", infName);
        InstanceIdentifier interfaceIdentifier = NeutronvpnUtils.buildVlanInterfaceIdentifier(infName);
        try {
            Optional<Interface> optionalInf = NeutronvpnUtils.read(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    interfaceIdentifier);
            if (!optionalInf.isPresent()) {
                wrtConfigTxn.put(LogicalDatastoreType.CONFIGURATION, interfaceIdentifier, inf);
            } else {
                LOG.warn("Interface {} is already present", infName);
            }
        } catch (Exception e) {
            LOG.error("failed to create interface {} due to the exception {} ", infName, e.getMessage());
        }
        return infName;
    }

    private Interface createInterface(Port port) {
        String interfaceName = port.getUuid().getValue();
        IfL2vlan.L2vlanMode l2VlanMode = IfL2vlan.L2vlanMode.Trunk;
        InterfaceBuilder interfaceBuilder = new InterfaceBuilder();
        IfL2vlanBuilder ifL2vlanBuilder = new IfL2vlanBuilder();
        ifL2vlanBuilder.setL2vlanMode(l2VlanMode);

        interfaceBuilder.setEnabled(true).setName(interfaceName).setType(L2vlan.class)
                .addAugmentation(IfL2vlan.class, ifL2vlanBuilder.build());

        if (NeutronvpnUtils.getPortSecurityEnabled(port)) {
            InterfaceAclBuilder interfaceAclBuilder = new InterfaceAclBuilder();
            interfaceAclBuilder.setPortSecurityEnabled(true);
            NeutronvpnUtils.populateInterfaceAclBuilder(interfaceAclBuilder, port);
            interfaceBuilder.addAugmentation(InterfaceAcl.class, interfaceAclBuilder.build());
        }
        return interfaceBuilder.build();
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void deleteOfPortInterface(Port port, WriteTransaction wrtConfigTxn) {
        String name = port.getUuid().getValue();
        LOG.debug("Removing OFPort Interface {}", name);
        InstanceIdentifier interfaceIdentifier = NeutronvpnUtils.buildVlanInterfaceIdentifier(name);
        try {
            Optional<Interface> optionalInf = NeutronvpnUtils.read(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    interfaceIdentifier);
            if (optionalInf.isPresent()) {
                wrtConfigTxn.delete(LogicalDatastoreType.CONFIGURATION, interfaceIdentifier);
            } else {
                LOG.error("Interface {} is not present", name);
            }
        } catch (Exception e) {
            LOG.error("Failed to delete interface {} due to the exception {}", name, e.getMessage());
        }
    }

    private void createElanInterface(Port port, String name, WriteTransaction wrtConfigTxn) {
        String elanInstanceName = port.getNetworkId().getValue();
        List<StaticMacEntries> staticMacEntries = NeutronvpnUtils.buildStaticMacEntry(port);

        InstanceIdentifier<ElanInterface> id = InstanceIdentifier.builder(ElanInterfaces.class).child(ElanInterface
                .class, new ElanInterfaceKey(name)).build();
        ElanInterface elanInterface = new ElanInterfaceBuilder().setElanInstanceName(elanInstanceName)
                .setName(name).setStaticMacEntries(staticMacEntries).setKey(new ElanInterfaceKey(name)).build();
        wrtConfigTxn.put(LogicalDatastoreType.CONFIGURATION, id, elanInterface);
        LOG.debug("Creating new ELan Interface {}", elanInterface);
    }

    private void deleteElanInterface(String name, WriteTransaction wrtConfigTxn) {
        InstanceIdentifier<ElanInterface> id = InstanceIdentifier.builder(ElanInterfaces.class).child(ElanInterface
                .class, new ElanInterfaceKey(name)).build();
        wrtConfigTxn.delete(LogicalDatastoreType.CONFIGURATION, id);
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void addToFloatingIpPortInfo(Uuid floatingIpId, Uuid floatingIpPortId, Uuid floatingIpPortSubnetId, String
                                         floatingIpPortMacAddress) {
        InstanceIdentifier id = buildfloatingIpIdToPortMappingIdentifier(floatingIpId);
        try {
            FloatingIpIdToPortMappingBuilder floatingipIdToPortMacMappingBuilder = new
                FloatingIpIdToPortMappingBuilder().setKey(new FloatingIpIdToPortMappingKey(floatingIpId))
                .setFloatingIpId(floatingIpId).setFloatingIpPortId(floatingIpPortId)
                .setFloatingIpPortSubnetId(floatingIpPortSubnetId)
                .setFloatingIpPortMacAddress(floatingIpPortMacAddress);
            LOG.debug("Creating floating IP UUID {} to Floating IP neutron port {} mapping in Floating IP"
                + " Port Info Config DS", floatingIpId.getValue(), floatingIpPortId.getValue());
            MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, id,
                floatingipIdToPortMacMappingBuilder.build());
        } catch (Exception e) {
            LOG.error("Creating floating IP UUID {} to Floating IP neutron port {} mapping in Floating IP"
                + " Port Info Config DS failed with exception {}",
                floatingIpId.getValue(), floatingIpPortId.getValue(), e);
        }
    }

    private Set<FixedIps> getFixedIpSet(List<FixedIps> fixedIps) {
        return fixedIps != null ? new HashSet<>(fixedIps) : Collections.emptySet();
    }
}
