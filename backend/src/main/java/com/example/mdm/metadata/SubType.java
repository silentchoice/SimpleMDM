package com.example.mdm.metadata;

public record SubType(long id, long masterTypeId, String code, String name, MetadataStatus status) {}
