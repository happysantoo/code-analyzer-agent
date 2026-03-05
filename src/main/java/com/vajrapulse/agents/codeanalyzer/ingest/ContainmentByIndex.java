package com.vajrapulse.agents.codeanalyzer.ingest;

/**
 * Containment: parent contains child, by symbol index.
 */
public record ContainmentByIndex(int parentIndex, int childIndex) {}
