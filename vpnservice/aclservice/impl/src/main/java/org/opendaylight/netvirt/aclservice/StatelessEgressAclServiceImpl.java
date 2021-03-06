/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchTcpFlags;
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchTcpDestinationPort;
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchTcpSourcePort;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager.Action;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.netvirt.aclservice.utils.AclConstants;
import org.opendaylight.netvirt.aclservice.utils.AclDataUtil;
import org.opendaylight.netvirt.aclservice.utils.AclServiceOFFlowBuilder;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Actions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.actions.packet.handling.Permit;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.AceType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceIp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttr;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides the stateless implementation for egress (w.r.t VM) ACL service.
 *
 * <p>
 * Note: Table names used are w.r.t switch. Hence, switch ingress is VM egress
 * and vice versa.
 */
public class StatelessEgressAclServiceImpl extends AbstractEgressAclServiceImpl {

    private static final Logger LOG = LoggerFactory.getLogger(StatelessEgressAclServiceImpl.class);

    public StatelessEgressAclServiceImpl(DataBroker dataBroker, IMdsalApiManager mdsalManager, AclDataUtil aclDataUtil,
            AclServiceUtils aclServiceUtils) {
        super(dataBroker, mdsalManager, aclDataUtil, aclServiceUtils);
    }

    @Override
    protected void programSpecificFixedRules(BigInteger dpid, String dhcpMacAddress,
            List<AllowedAddressPairs> allowedAddresses, int lportTag, String portId, Action action, int addOrRemove) {
    }

    @Override
    protected String syncSpecificAclFlow(BigInteger dpId, int lportTag, int addOrRemove, Ace ace, String portId,
            Map<String, List<MatchInfoBase>> flowMap, String flowName) {
        // Not in use here. programAceRule function is overridden.
        return null;
    }

    @Override
    protected void programAceRule(AclInterface port, int addOrRemove, String aclName, Ace ace,
            List<AllowedAddressPairs> syncAllowedAddresses) {
        SecurityRuleAttr aceAttr = AclServiceUtils.getAccesssListAttributes(ace);
        if (!aceAttr.getDirection().equals(DirectionEgress.class)) {
            return;
        }
        Matches matches = ace.getMatches();
        AceType aceType = matches.getAceType();
        Map<String, List<MatchInfoBase>> flowMap = null;
        Short protocol = null;

        if (aceType instanceof AceIp) {
            protocol = ((AceIp)aceType).getProtocol();
            flowMap = AclServiceOFFlowBuilder.programIpFlow(matches);
        }
        if (null == flowMap) {
            LOG.error("Failed to apply ACL {}", ace.getKey());
            return;
        }
        for (Map.Entry<String, List<MatchInfoBase>> flow : flowMap.entrySet()) {
            String flowName = flow.getKey();
            List<MatchInfoBase> flowMatches = flow.getValue();
            boolean hasTcpMatch = AclServiceUtils.containsMatchFieldType(flowMatches,
                    NxMatchTcpDestinationPort.class) || AclServiceUtils.containsMatchFieldType(flowMatches,
                    NxMatchTcpSourcePort.class);
            if (hasTcpMatch || protocol == null) {
                int lportTag = port.getLPortTag();
                flowName += "Egress" + lportTag + ace.getKey().getRuleName();
                flowMatches.add(buildLPortTagMatch(lportTag));
                programSynRules(port.getDpId(), flowName, flowMatches, addOrRemove, protocol, ace.getActions());
            }
        }
    }

    private void programSynRules(BigInteger dpId, String origFlowName,
            List<MatchInfoBase> origFlowMatches, int addFlow, Short protocol, Actions actions) {
        List<MatchInfoBase> flowMatches = new ArrayList<>();
        flowMatches.addAll(origFlowMatches);
        if (new Short((short) NwConstants.IP_PROT_TCP).equals(protocol)) {
            flowMatches.add(MatchTcpFlags.SYN);
        }

        String flowName = "SYN_" + origFlowName;
        syncFlow(dpId, NwConstants.INGRESS_ACL_TABLE, flowName, AclConstants.PROTO_MATCH_SYN_ALLOW_PRIORITY,
                "ACL_SYN_", 0, 0, AclConstants.COOKIE_ACL_BASE, flowMatches, getSynRulesInstructions(actions), addFlow);
        String oper = getOperAsString(addFlow);
        LOG.debug("{} {} syn packet flow {}", oper, actions, flowName);
    }

    private List<InstructionInfo> getSynRulesInstructions(Actions actions) {
        if (actions != null && actions.getPacketHandling() instanceof Permit) {
            List<ActionInfo> actionsInfos = new ArrayList<>();
            return getDispatcherTableResubmitInstructions(actionsInfos);
        } else {
            return AclServiceOFFlowBuilder.getDropInstructionInfo();
        }
    }
}
