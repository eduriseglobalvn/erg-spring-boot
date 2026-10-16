package com.erg.ergspringboot.enterpriseconnectors;

import com.erg.ergspringboot.platform.events.ModuleEventMetadata;

/** Marker for the bounded event catalog of the connector module. */
public interface EnterpriseConnectorEvent {
    ModuleEventMetadata metadata();
}
