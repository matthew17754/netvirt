/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests

import org.opendaylight.genius.mdsalutil.MetaDataUtil
import org.opendaylight.genius.mdsalutil.actions.ActionDrop
import org.opendaylight.genius.mdsalutil.actions.ActionNxConntrack
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit
import org.opendaylight.genius.mdsalutil.FlowEntityBuilder
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetDestination
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetSource
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType
import org.opendaylight.genius.mdsalutil.matches.MatchIcmpv6
import org.opendaylight.genius.mdsalutil.matches.MatchIpProtocol
import org.opendaylight.genius.mdsalutil.matches.MatchIpv6Destination
import org.opendaylight.genius.mdsalutil.matches.MatchIpv6Source
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchCtState
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchRegister
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchTcpDestinationPort
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchUdpDestinationPort
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg6

import static extension org.opendaylight.mdsal.binding.testutils.XtendBuilderExtensions.operator_doubleGreaterThan

class FlowEntryObjectsStatefulIPv6 extends FlowEntryObjectsStateful {

    override fixedConntrackIngressFlowsPort1() {
        #[
           new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Conntrk_123_0D:AA:D8:42:30:F3_2001:db8:1::/64_Recirc"
            flowName = "ACL"
            instructionInfoList = #[
                new InstructionApplyActions(#[
                    new ActionNxConntrack(2, 0, 0, 5000, 243 as short)
                ])
            ]
            matchInfoList = #[
                new MatchEthernetType(34525L),
                new MatchEthernetDestination(new MacAddress("0D:AA:D8:42:30:F3")),
                new MatchEthernetType(34525L),
                new MatchIpv6Destination("2001:db8:1::/64")
                ]
                priority = 61010
                tableId = 241 as short
            ],
           new FlowEntityBuilder >> [
                dpnId = 123bi
               cookie = 110100481bi
               flowId = "Ingress_Fixed_Conntrk_Drop123_987_Tracked_New"
               flowName = "ACL"
               instructionInfoList = #[
                   new InstructionApplyActions(#[
                       new ActionDrop()
                   ])
               ]
               matchInfoList = #[
                   new MatchMetadata(1085217976614912bi, 1152920405095219200bi),
                   new NxMatchCtState(33, 33)
               ]
               priority = 50
               tableId = 213 as short
           ],
           new FlowEntityBuilder >> [
                dpnId = 123bi
               cookie = 110100481bi
               flowId = "Ingress_Fixed_Conntrk_Drop123_987_Tracked_Invalid"
               flowName = "ACL"
               instructionInfoList = #[
                   new InstructionApplyActions(#[
                       new ActionDrop()
                   ])
               ]
               matchInfoList = #[
                   new MatchMetadata(1085217976614912bi, 1152920405095219200bi),
                   new NxMatchCtState(48, 48)
               ]
               priority = 62015
               tableId = 213 as short
           ]
        ]
    }

    override etherIngressFlowsPort2() {
        val flowId1 = "ETHERnull_ipv6_remoteACL_interface_aap_0D:AA:D8:42:30:F3_2001:db8:1::/64Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
        val flowId2 = "ETHERnull_ipv6_remoteACL_interface_aap_0D:AA:D8:42:30:F4_2001:db8:2::/64Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId1
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("2001:db8:1::/64"),
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("::/0"),
                    new MatchEthernetType(34525L),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new NxMatchCtState(33, 33)
                ]
                priority = IdHelper.getId(flowId1)
                tableId = 243 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId2
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("2001:db8:2::/64"),
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("::/0"),
                    new MatchEthernetType(34525L),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new NxMatchCtState(33, 33)
                ]
                priority = IdHelper.getId(flowId2)
                tableId = 243 as short
            ]
        ]
    }

    override fixedConntrackEgressFlowsPort1() {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_Fixed_Conntrk_123_0D:AA:D8:42:30:F3_2001:db8:1::/64_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, 213 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F3")),
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("2001:db8:1::/64")
                ]
                priority = 61010
                tableId = 211 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100481bi
                flowId = "Egress_Fixed_Conntrk_Drop123_987_Tracked_New"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new NxMatchCtState(33, 33)
                ]
                priority = 50
                tableId = 243 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100481bi
                flowId = "Egress_Fixed_Conntrk_Drop123_987_Tracked_Invalid"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new NxMatchCtState(48, 48)
                ]
                priority = 62015
                tableId = 243 as short
            ]
        ]
    }

    override fixedConntrackIngressFlowsPort2() {
        #[
             new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Conntrk_123_0D:AA:D8:42:30:F4_2001:db8:2::/64_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, 243 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetDestination(new MacAddress("0D:AA:D8:42:30:F4")),
                    new MatchEthernetType(34525L),
                    new MatchIpv6Destination("2001:db8:2::/64")
                ]
                priority = 61010
                tableId = 241 as short
             ],
             new FlowEntityBuilder >> [
                dpnId = 123bi
                 cookie = 110100481bi
                 flowId = "Ingress_Fixed_Conntrk_Drop123_987_Tracked_New"
                 flowName = "ACL"
                 instructionInfoList = #[
                     new InstructionApplyActions(#[
                         new ActionDrop()
                     ])
                 ]
                 matchInfoList = #[
                     new MatchMetadata(1085217976614912bi, 1152920405095219200bi),
                     new NxMatchCtState(33, 33)
                 ]
                 priority = 50
                 tableId = 213 as short
             ],
             new FlowEntityBuilder >> [
                dpnId = 123bi
                 cookie = 110100481bi
                 flowId = "Ingress_Fixed_Conntrk_Drop123_987_Tracked_Invalid"
                 flowName = "ACL"
                 instructionInfoList = #[
                     new InstructionApplyActions(#[
                         new ActionDrop()
                     ])
                 ]
                 matchInfoList = #[
                     new MatchMetadata(1085217976614912bi, 1152920405095219200bi),
                     new NxMatchCtState(48, 48)
                 ]
                 priority = 62015
                 tableId = 213 as short
             ]

        ]
    }

    override fixedConntrackEgressFlowsPort2() {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_Fixed_Conntrk_123_0D:AA:D8:42:30:F4_2001:db8:2::/64_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, 213 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F4")),
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("2001:db8:2::/64")
                ]
                priority = 61010
                tableId = 211 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100481bi
                flowId = "Egress_Fixed_Conntrk_Drop123_987_Tracked_New"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new NxMatchCtState(33, 33)
                ]
                priority = 50
                tableId = 243 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100481bi
                flowId = "Egress_Fixed_Conntrk_Drop123_987_Tracked_Invalid"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new NxMatchCtState(48, 48)
                ]
                priority = 62015
                tableId = 243 as short
            ]
        ]
    }

    override fixedConntrackIngressFlowsPort3() {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Conntrk_123_0D:AA:D8:42:30:F5_2001:db8:3::/64_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, 243 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetDestination(new MacAddress("0D:AA:D8:42:30:F5")),
                    new MatchEthernetType(34525L),
                    new MatchIpv6Destination("2001:db8:3::/64")
                ]
                priority = 61010
                tableId = 241 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100481bi
                flowId = "Ingress_Fixed_Conntrk_Drop123_987_Tracked_New"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614912bi, 1152920405095219200bi),
                    new NxMatchCtState(33, 33)
                ]
                priority = 50
                tableId = 213 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100481bi
                flowId = "Ingress_Fixed_Conntrk_Drop123_987_Tracked_Invalid"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614912bi, 1152920405095219200bi),
                    new NxMatchCtState(48, 48)
                ]
                priority = 62015
                tableId = 213 as short
            ]
        ]
    }

    override fixedConntrackEgressFlowsPort3() {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_Fixed_Conntrk_123_0D:AA:D8:42:30:F5_2001:db8:3::/64_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, 213 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F5")),
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("2001:db8:3::/64")
                ]
                priority = 61010
                tableId = 211 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100481bi
                flowId = "Egress_Fixed_Conntrk_Drop123_987_Tracked_New"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new NxMatchCtState(33, 33)
                ]
                priority = 50
                tableId = 243 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100481bi
                flowId = "Egress_Fixed_Conntrk_Drop123_987_Tracked_Invalid"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new MatchMetadata(1085217976614912bi, 1152920405095219200bi),
                    new NxMatchCtState(48, 48)
                ]
                priority = 62015
                tableId = 243 as short
            ]
        ]
    }

    override etherEgressFlowsPort1() {
        val theFlowId = "ETHERnullEgress98785cc3048-abc3-43cc-89b3-377341426ac6"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("::/0"),
                    new MatchEthernetType(34525L),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchCtState(33, 33)
                ]
                priority = IdHelper.getId(theFlowId)
                tableId = 213 as short
            ]
        ]
    }

    override etheregressFlowPort2() {
        val theFlowId = "ETHERnullEgress98785cc3048-abc3-43cc-89b3-377341426ac6"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("::/0"),
                    new MatchEthernetType(34525L),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchCtState(33, 33)
                ]
                priority = IdHelper.getId(theFlowId)
                tableId = 213 as short
            ]
        ]
    }

    override tcpIngressFlowPort1() {
        val theFlowId = "TCP_DESTINATION_80_65535Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("::/0"),
                    new MatchEthernetType(34525L),
                    new NxMatchTcpDestinationPort(80, 65535),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchCtState(33, 33)
                ]
                priority = IdHelper.getId(theFlowId)
                tableId = 243 as short
            ]
        ]
    }

    override tcpIngressFlowPort2() {
        val theFlowId = "TCP_DESTINATION_80_65535Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("::/0"),
                    new MatchEthernetType(34525L),
                    new NxMatchTcpDestinationPort(80, 65535),
                    new MatchIpProtocol(6 as short),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new NxMatchCtState(33, 33)
                ]
                priority = IdHelper.getId(theFlowId)
                tableId = 243 as short
            ]
        ]
    }

    override tcpEgressFlowPort2() {
        val flowId1 = "TCP_DESTINATION_80_65535_ipv6_remoteACL_interface_aap_0D:AA:D8:42:30:F3_2001:db8:1::/64Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
        val flowId2 = "TCP_DESTINATION_80_65535_ipv6_remoteACL_interface_aap_0D:AA:D8:42:30:F4_2001:db8:2::/64Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId1
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpv6Destination("2001:db8:1::/64"),
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("::/0"),
                    new MatchEthernetType(34525L),
                    new NxMatchTcpDestinationPort(80, 65535),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchCtState(33, 33)
                ]
                priority = IdHelper.getId(flowId1)
                tableId = 213 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId1
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpv6Destination("2001:db8:2::/64"),
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("::/0"),
                    new MatchEthernetType(34525L),
                    new NxMatchTcpDestinationPort(80, 65535),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchCtState(33, 33)
                ]
                priority = IdHelper.getId(flowId2)
                tableId = 213 as short
            ]
        ]
    }

    override udpEgressFlowsPort1() {
        val theFlowId = "UDP_DESTINATION_80_65535Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
        #[
             new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("::/0"),
                    new MatchEthernetType(34525L),
                    new NxMatchUdpDestinationPort(80, 65535),
                    new MatchIpProtocol(17 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchCtState(33, 33)
                ]
                priority = IdHelper.getId(theFlowId)
                tableId = 213 as short
            ]
        ]
    }

    override udpIngressFlowsPort2() {
        val flowId1 = "UDP_DESTINATION_80_65535_ipv6_remoteACL_interface_aap_0D:AA:D8:42:30:F3_2001:db8:1::/64Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
        val flowId2 = "UDP_DESTINATION_80_65535_ipv6_remoteACL_interface_aap_0D:AA:D8:42:30:F4_2001:db8:2::/64Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId1
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("2001:db8:1::/64"),
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("::/0"),
                    new MatchEthernetType(34525L),
                    new NxMatchUdpDestinationPort(80, 65535),
                    new MatchIpProtocol(17 as short),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new NxMatchCtState(33, 33)
                ]
                priority = IdHelper.getId(flowId1)
                tableId = 243 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId2
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("2001:db8:2::/64"),
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("::/0"),
                    new MatchEthernetType(34525L),
                    new NxMatchUdpDestinationPort(80, 65535),
                    new MatchIpProtocol(17 as short),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new NxMatchCtState(33, 33)
                ]
                priority = IdHelper.getId(flowId2)
                tableId = 243 as short
            ]
        ]
    }

    override udpEgressFlowsPort2() {
        val theFlowId = "UDP_DESTINATION_80_65535Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("::/0"),
                    new MatchEthernetType(34525L),
                    new NxMatchUdpDestinationPort(80, 65535),
                    new MatchIpProtocol(17 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchCtState(33, 33)
                ]
                priority = IdHelper.getId(theFlowId)
                tableId = 213 as short
            ]
        ]
    }

    override icmpIngressFlowsPort1() {
        val theFlowId = "ICMP_V6_DESTINATION_23_Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("::/0"),
                    new MatchEthernetType(34525L),
                    new MatchIcmpv6(2 as short, 3 as short),
                    new MatchIpProtocol(1 as short),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new NxMatchCtState(33, 33)
                ]
                priority = IdHelper.getId(theFlowId)
                tableId = 243 as short
            ]
        ]
    }

    override icmpIngressFlowsPort2() {
        val theFlowId = "ICMP_V6_DESTINATION_23_Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("::/0"),
                    new MatchEthernetType(34525L),
                    new MatchIcmpv6(2 as short, 3 as short),
                    new MatchIpProtocol(1 as short),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new NxMatchCtState(33, 33)
                ]
                priority = IdHelper.getId(theFlowId)
                tableId = 243 as short
            ]
        ]
    }

    override icmpEgressFlowsPort2() {
        val flowId1 = "ICMP_V6_DESTINATION_23__ipv6_remoteACL_interface_aap_0D:AA:D8:42:30:F3_2001:db8:1::/64Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
        val flowId2 = "ICMP_V6_DESTINATION_23__ipv6_remoteACL_interface_aap_0D:AA:D8:42:30:F4_2001:db8:2::/64Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId1
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpv6Destination("2001:db8:1::/64"),
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("::/0"),
                    new MatchEthernetType(34525L),
                    new MatchIcmpv6(2 as short, 3 as short),
                    new MatchIpProtocol(1 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchCtState(33, 33)
                ]
                priority = IdHelper.getId(flowId1)
                tableId = 213 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId2
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpv6Destination("2001:db8:2::/64"),
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("::/0"),
                    new MatchEthernetType(34525L),
                    new MatchIcmpv6(2 as short, 3 as short),
                    new MatchIpProtocol(1 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchCtState(33, 33)
                ]
                priority = IdHelper.getId(flowId2)
                tableId = 213 as short
            ]
        ]
    }

    override udpIngressPortRangeFlows() {
        val theFlowId = "UDP_DESTINATION_2000_65532Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("::/0"),
                    new MatchEthernetType(34525L),
                    new NxMatchUdpDestinationPort(2000, 65532),
                    new MatchIpProtocol(17 as short),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new NxMatchCtState(33, 33)
                ]
                priority = IdHelper.getId(theFlowId)
                tableId = 243 as short
            ]
        ]
    }

    override tcpEgressRangeFlows() {
        val flowId1 = "TCP_DESTINATION_776_65534Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
        val flowId2 = "TCP_DESTINATION_512_65280Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
        val flowId3 = "TCP_DESTINATION_334_65534Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
        val flowId4 = "TCP_DESTINATION_333_65535Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
        val flowId5 = "TCP_DESTINATION_336_65520Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
        val flowId6 = "TCP_DESTINATION_352_65504Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
        val flowId7 = "TCP_DESTINATION_384_65408Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
        val flowId8 = "TCP_DESTINATION_768_65528Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId1
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("::/0"),
                    new MatchEthernetType(34525L),
                    new NxMatchTcpDestinationPort(776, 65534),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchCtState(33, 33)
                ]
                priority = IdHelper.getId(flowId1)
                tableId = 213 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId2
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("::/0"),
                    new MatchEthernetType(34525L),
                    new NxMatchTcpDestinationPort(512, 65280),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchCtState(33, 33)
                ]
                priority = IdHelper.getId(flowId2)
                tableId = 213 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId3
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("::/0"),
                    new MatchEthernetType(34525L),
                    new NxMatchTcpDestinationPort(334, 65534),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchCtState(33, 33)
                ]
                priority = IdHelper.getId(flowId3)
                tableId = 213 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId4
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("::/0"),
                    new MatchEthernetType(34525L),
                    new NxMatchTcpDestinationPort(333, 65535),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchCtState(33, 33)
                ]
                priority = IdHelper.getId(flowId4)
                tableId = 213 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId5
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("::/0"),
                    new MatchEthernetType(34525L),
                    new NxMatchTcpDestinationPort(336, 65520),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchCtState(33, 33)
                ]
                priority = IdHelper.getId(flowId5)
                tableId = 213 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId6
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("::/0"),
                    new MatchEthernetType(34525L),
                    new NxMatchTcpDestinationPort(352, 65504),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchCtState(33, 33)
                ]
                priority = IdHelper.getId(flowId6)
                tableId = 213 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId7
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("::/0"),
                    new MatchEthernetType(34525L),
                    new NxMatchTcpDestinationPort(384, 65408),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchCtState(33, 33)
                ]
                priority = IdHelper.getId(flowId7)
                tableId = 213 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId8
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("::/0"),
                    new MatchEthernetType(34525L),
                    new NxMatchTcpDestinationPort(768, 65528),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchCtState(33, 33)
                ]
                priority = IdHelper.getId(flowId8)
                tableId = 213 as short
            ]
        ]
    }

    override udpIngressAllFlows() {
        val theFlowId = "UDP_DESTINATION_1_0Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("::/0"),
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(17 as short),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new NxMatchCtState(33, 33)
                ]
                priority = IdHelper.getId(theFlowId)
                tableId = 243 as short
            ]
        ]
    }

    override tcpEgressAllFlows() {
        val theFlowId = "TCP_DESTINATION_1_0Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
         #[
             new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("::/0"),
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchCtState(33, 33)
                ]
                priority = IdHelper.getId(theFlowId)
                tableId = 213 as short
            ]
         ]

     }

    override icmpIngressFlowsPort3() {
        val flowId1 = "ICMP_V6_DESTINATION_23_Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
        val flowId2 = "ICMP_V6_DESTINATION_23_Ingress98785cc3048-abc3-43cc-89b3-377341426a22"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId1
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("::/0"),
                    new MatchEthernetType(34525L),
                    new MatchIcmpv6(2 as short, 3 as short),
                    new MatchIpProtocol(1 as short),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new NxMatchCtState(33, 33)
                ]
                priority = IdHelper.getId(flowId1)
                tableId = 243 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId2
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("::/0"),
                    new MatchEthernetType(34525L),
                    new MatchIcmpv6(2 as short, 3 as short),
                    new MatchIpProtocol(1 as short),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new NxMatchCtState(33, 33)
                ]
                priority = IdHelper.getId(flowId2)
                tableId = 243 as short
            ]
        ]
    }

    override icmpEgressFlowsPort3() {
        val flowId1 = "ICMP_V6_DESTINATION_23_Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
        val flowId2 = "ICMP_V6_DESTINATION_23_Egress98785cc3048-abc3-43cc-89b3-377341426a21"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId1
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("::/0"),
                    new MatchEthernetType(34525L),
                    new MatchIcmpv6(2 as short, 3 as short),
                    new MatchIpProtocol(1 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchCtState(33, 33)
                ]
                priority = IdHelper.getId(flowId1)
                tableId = 213 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId2
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source("::/0"),
                    new MatchEthernetType(34525L),
                    new MatchIcmpv6(2 as short, 3 as short),
                    new MatchIpProtocol(1 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchCtState(33, 33)
                ]
                priority = IdHelper.getId(flowId2)
                tableId = 213 as short
            ]
        ]
    }
}
