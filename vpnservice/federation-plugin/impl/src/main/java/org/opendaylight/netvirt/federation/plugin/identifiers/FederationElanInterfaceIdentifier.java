/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.federation.plugin.identifiers;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.federation.plugin.FederationPluginConstants;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@Singleton
public class FederationElanInterfaceIdentifier
        implements FederationPluginIdentifier<ElanInterface, ElanInterfaces, ElanInterfaces> {

    @Inject
    public FederationElanInterfaceIdentifier() {
        FederationPluginIdentifierRegistry.registerIdentifier(FederationPluginConstants.ELAN_INTERFACE_KEY,
                LogicalDatastoreType.CONFIGURATION, this);
    }

    @Override
    public InstanceIdentifier<ElanInterface> getInstanceIdentifier() {
        return InstanceIdentifier.create(ElanInterfaces.class).child(ElanInterface.class);
    }

    @Override
    public InstanceIdentifier<ElanInterfaces> getParentInstanceIdentifier() {
        return InstanceIdentifier.create(ElanInterfaces.class);
    }

    @Override
    public InstanceIdentifier<ElanInterfaces> getSubtreeInstanceIdentifier() {
        return InstanceIdentifier.create(ElanInterfaces.class);
    }

}
