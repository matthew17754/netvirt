/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests

import java.util.List
import javax.annotation.concurrent.NotThreadSafe
import org.opendaylight.netvirt.aclservice.tests.infra.DataTreeIdentifierDataObjectPairBuilder
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAcl
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAclBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier

import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION

import static extension org.opendaylight.mdsal.binding.testutils.XtendBuilderExtensions.operator_doubleGreaterThan
import java.util.ArrayList

@NotThreadSafe
class IdentifiedInterfaceWithAclBuilder implements DataTreeIdentifierDataObjectPairBuilder<Interface> {

    var String interfaceName
    var Boolean portSecurity
    var List<Uuid> newSecurityGroups = new ArrayList
    var List<AllowedAddressPairs> ifAllowedAddressPairs = new ArrayList

    override type() {
        CONFIGURATION
    }

    override identifier() {
        InstanceIdentifier.builder(Interfaces)
                    .child(Interface, new InterfaceKey(interfaceName)).build
    }

    override dataObject() {
        new InterfaceBuilder >> [
            addAugmentation(InterfaceAcl, new InterfaceAclBuilder >> [
                portSecurityEnabled = portSecurity
                securityGroups = newSecurityGroups
                allowedAddressPairs = ifAllowedAddressPairs
            ])
            name = interfaceName
        ]
    }

    // see IdentifiedAceBuilder (@Builder)

    def interfaceName(String interfaceName) {
        this.interfaceName = interfaceName
        this
    }

    def portSecurity(Boolean portSecurity) {
        this.portSecurity = portSecurity
        this
    }

    def addAllNewSecurityGroups(List<Uuid> newSecurityGroups) {
        this.newSecurityGroups.addAll(newSecurityGroups)
        this
    }

    def addAllIfAllowedAddressPairs(List<AllowedAddressPairs> ifAllowedAddressPairs) {
        this.ifAllowedAddressPairs.addAll(ifAllowedAddressPairs)
        this
    }

}
