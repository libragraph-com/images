// C-IMAGES §2 — applies the verb core as a pure aggregate over its image modules (ADR-052, BFR-004 Scope)

plugins {
    id("com.libragraph.build-tools")
    // The MUSTr engine's plugin and the house lanes, on the root's class path so each image module's application of the house plugin
    // finds the engine's task types in the same class loader (C-SHARED-MUSTR §6): every module's boot check runs in its `gate`.
    id("com.libragraph.mustr") apply false
    id("com.libragraph.shared-mustr") apply false
}

allprojects {
    // Third-party coordinates (the checks' engine and libraries); first-party ones the convention plugin resolves (BFR-002).
    repositories {
        mavenCentral()
    }
}
