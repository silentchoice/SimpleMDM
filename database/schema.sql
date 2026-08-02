/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) DEFAULT NULL,
  `description` varchar(200) NOT NULL,
  `type` varchar(20) NOT NULL,
  `script` varchar(1000) NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mdm_child_field_definition` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `child_type_id` bigint NOT NULL,
  `field_key` varchar(64) NOT NULL,
  `field_name` varchar(128) NOT NULL,
  `data_type` varchar(16) NOT NULL,
  `required` tinyint(1) NOT NULL DEFAULT '0',
  `unique_value` tinyint(1) NOT NULL DEFAULT '0',
  `searchable` tinyint(1) NOT NULL DEFAULT '0',
  `max_length` int DEFAULT NULL,
  `precision_value` int DEFAULT NULL,
  `scale_value` int DEFAULT NULL,
  `reference_object_type_id` bigint DEFAULT NULL,
  `default_value` varchar(2048) DEFAULT NULL,
  `validation_rule` varchar(2048) DEFAULT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `status` varchar(32) NOT NULL,
  `created_at` datetime NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL,
  `updated_by` bigint DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `system_id` bigint NOT NULL,
  `shared` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_child_field_definition_key` (`child_type_id`,`field_key`),
  UNIQUE KEY `uk_child_field_system_id` (`system_id`,`id`),
  KEY `fk_child_field_definition_ref` (`reference_object_type_id`),
  KEY `fk_child_field_definition_created` (`created_by`),
  KEY `fk_child_field_definition_updated` (`updated_by`),
  KEY `fk_child_field_type_system` (`system_id`,`child_type_id`),
  KEY `fk_child_field_reference_system` (`system_id`,`reference_object_type_id`),
  KEY `fk_child_field_created_system` (`system_id`,`created_by`),
  KEY `fk_child_field_updated_system` (`system_id`,`updated_by`),
  CONSTRAINT `fk_child_field_created_system` FOREIGN KEY (`system_id`, `created_by`) REFERENCES `sys_user` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_child_field_definition_created` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_child_field_definition_ref` FOREIGN KEY (`reference_object_type_id`) REFERENCES `mdm_object_type` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_child_field_definition_type` FOREIGN KEY (`child_type_id`) REFERENCES `mdm_child_type` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_child_field_definition_updated` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_child_field_reference_system` FOREIGN KEY (`system_id`, `reference_object_type_id`) REFERENCES `mdm_object_type` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_child_field_type_system` FOREIGN KEY (`system_id`, `child_type_id`) REFERENCES `mdm_child_type` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_child_field_updated_system` FOREIGN KEY (`system_id`, `updated_by`) REFERENCES `sys_user` (`system_id`, `id`) ON DELETE RESTRICT
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mdm_child_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `record_id` bigint NOT NULL,
  `child_type_id` bigint NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `status` varchar(32) NOT NULL,
  `created_at` datetime NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL,
  `updated_by` bigint DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `deleted_at` datetime DEFAULT NULL,
  `system_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_child_record_system_id` (`system_id`,`id`),
  KEY `fk_child_record_record` (`record_id`),
  KEY `fk_child_record_type` (`child_type_id`),
  KEY `fk_child_record_created_by` (`created_by`),
  KEY `fk_child_record_updated_by` (`updated_by`),
  KEY `fk_child_record_record_system` (`system_id`,`record_id`),
  KEY `fk_child_record_type_system` (`system_id`,`child_type_id`),
  KEY `fk_child_record_created_system` (`system_id`,`created_by`),
  KEY `fk_child_record_updated_system` (`system_id`,`updated_by`),
  CONSTRAINT `fk_child_record_created_by` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_child_record_created_system` FOREIGN KEY (`system_id`, `created_by`) REFERENCES `sys_user` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_child_record_record` FOREIGN KEY (`record_id`) REFERENCES `mdm_record` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_child_record_record_system` FOREIGN KEY (`system_id`, `record_id`) REFERENCES `mdm_record` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_child_record_type` FOREIGN KEY (`child_type_id`) REFERENCES `mdm_child_type` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_child_record_type_system` FOREIGN KEY (`system_id`, `child_type_id`) REFERENCES `mdm_child_type` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_child_record_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_child_record_updated_system` FOREIGN KEY (`system_id`, `updated_by`) REFERENCES `sys_user` (`system_id`, `id`) ON DELETE RESTRICT
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mdm_child_record_value` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `child_record_id` bigint NOT NULL,
  `field_definition_id` bigint NOT NULL,
  `string_value` varchar(4096) DEFAULT NULL,
  `text_value` text,
  `integer_value` bigint DEFAULT NULL,
  `decimal_value` decimal(38,10) DEFAULT NULL,
  `boolean_value` tinyint(1) DEFAULT NULL,
  `date_value` date DEFAULT NULL,
  `datetime_value` datetime DEFAULT NULL,
  `reference_record_id` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL,
  `updated_by` bigint DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `system_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_child_record_field` (`child_record_id`,`field_definition_id`),
  KEY `fk_child_value_field` (`field_definition_id`),
  KEY `fk_child_value_reference` (`reference_record_id`),
  KEY `fk_child_value_created_by` (`created_by`),
  KEY `fk_child_value_updated_by` (`updated_by`),
  KEY `fk_child_value_record_system` (`system_id`,`child_record_id`),
  KEY `fk_child_value_field_system` (`system_id`,`field_definition_id`),
  KEY `fk_child_value_reference_system` (`system_id`,`reference_record_id`),
  KEY `fk_child_value_created_system` (`system_id`,`created_by`),
  KEY `fk_child_value_updated_system` (`system_id`,`updated_by`),
  CONSTRAINT `fk_child_value_created_by` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_child_value_created_system` FOREIGN KEY (`system_id`, `created_by`) REFERENCES `sys_user` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_child_value_field` FOREIGN KEY (`field_definition_id`) REFERENCES `mdm_child_field_definition` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_child_value_field_system` FOREIGN KEY (`system_id`, `field_definition_id`) REFERENCES `mdm_child_field_definition` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_child_value_record` FOREIGN KEY (`child_record_id`) REFERENCES `mdm_child_record` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_child_value_record_system` FOREIGN KEY (`system_id`, `child_record_id`) REFERENCES `mdm_child_record` (`system_id`, `id`) ON DELETE CASCADE,
  CONSTRAINT `fk_child_value_reference` FOREIGN KEY (`reference_record_id`) REFERENCES `mdm_record` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_child_value_reference_system` FOREIGN KEY (`system_id`, `reference_record_id`) REFERENCES `mdm_record` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_child_value_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_child_value_updated_system` FOREIGN KEY (`system_id`, `updated_by`) REFERENCES `sys_user` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_child_record_value_one_type` CHECK ((((((((((case when (`string_value` is not null) then 1 else 0 end) + (case when (`text_value` is not null) then 1 else 0 end)) + (case when (`integer_value` is not null) then 1 else 0 end)) + (case when (`decimal_value` is not null) then 1 else 0 end)) + (case when (`boolean_value` is not null) then 1 else 0 end)) + (case when (`date_value` is not null) then 1 else 0 end)) + (case when (`datetime_value` is not null) then 1 else 0 end)) + (case when (`reference_record_id` is not null) then 1 else 0 end)) <= 1))
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mdm_child_type` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `object_type_id` bigint NOT NULL,
  `code` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `description` varchar(512) DEFAULT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `status` varchar(32) NOT NULL,
  `created_at` datetime NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL,
  `updated_by` bigint DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `system_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_child_type_code` (`object_type_id`,`code`),
  UNIQUE KEY `uk_child_type_system_id` (`system_id`,`id`),
  KEY `fk_child_type_created_by` (`created_by`),
  KEY `fk_child_type_updated_by` (`updated_by`),
  KEY `fk_child_type_object_system` (`system_id`,`object_type_id`),
  KEY `fk_child_type_created_system` (`system_id`,`created_by`),
  KEY `fk_child_type_updated_system` (`system_id`,`updated_by`),
  CONSTRAINT `fk_child_type_created_by` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_child_type_created_system` FOREIGN KEY (`system_id`, `created_by`) REFERENCES `sys_user` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_child_type_object` FOREIGN KEY (`object_type_id`) REFERENCES `mdm_object_type` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_child_type_object_system` FOREIGN KEY (`system_id`, `object_type_id`) REFERENCES `mdm_object_type` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_child_type_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_child_type_updated_system` FOREIGN KEY (`system_id`, `updated_by`) REFERENCES `sys_user` (`system_id`, `id`) ON DELETE RESTRICT
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mdm_field_definition` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `object_type_id` bigint NOT NULL,
  `field_key` varchar(64) NOT NULL,
  `field_name` varchar(128) NOT NULL,
  `data_type` varchar(16) NOT NULL,
  `required` tinyint(1) NOT NULL DEFAULT '0',
  `unique_value` tinyint(1) NOT NULL DEFAULT '0',
  `searchable` tinyint(1) NOT NULL DEFAULT '0',
  `shared` tinyint(1) NOT NULL DEFAULT '0',
  `max_length` int DEFAULT NULL,
  `precision_value` int DEFAULT NULL,
  `scale_value` int DEFAULT NULL,
  `reference_object_type_id` bigint DEFAULT NULL,
  `default_value` varchar(2048) DEFAULT NULL,
  `validation_rule` varchar(2048) DEFAULT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `status` varchar(32) NOT NULL,
  `created_at` datetime NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL,
  `updated_by` bigint DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `system_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_field_definition_key` (`object_type_id`,`field_key`),
  UNIQUE KEY `uk_field_system_id` (`system_id`,`id`),
  KEY `fk_field_definition_reference` (`reference_object_type_id`),
  KEY `fk_field_definition_created_by` (`created_by`),
  KEY `fk_field_definition_updated_by` (`updated_by`),
  KEY `fk_field_object_system` (`system_id`,`object_type_id`),
  KEY `fk_field_reference_system` (`system_id`,`reference_object_type_id`),
  KEY `fk_field_created_system` (`system_id`,`created_by`),
  KEY `fk_field_updated_system` (`system_id`,`updated_by`),
  CONSTRAINT `fk_field_created_system` FOREIGN KEY (`system_id`, `created_by`) REFERENCES `sys_user` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_field_definition_created_by` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_field_definition_reference` FOREIGN KEY (`reference_object_type_id`) REFERENCES `mdm_object_type` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_field_definition_type` FOREIGN KEY (`object_type_id`) REFERENCES `mdm_object_type` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_field_definition_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_field_object_system` FOREIGN KEY (`system_id`, `object_type_id`) REFERENCES `mdm_object_type` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_field_reference_system` FOREIGN KEY (`system_id`, `reference_object_type_id`) REFERENCES `mdm_object_type` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_field_updated_system` FOREIGN KEY (`system_id`, `updated_by`) REFERENCES `sys_user` (`system_id`, `id`) ON DELETE RESTRICT
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mdm_metadata_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `system_id` bigint NOT NULL,
  `actor_id` bigint NOT NULL,
  `entity_type` varchar(32) NOT NULL,
  `entity_id` bigint NOT NULL,
  `action` varchar(32) NOT NULL,
  `before_snapshot` text,
  `after_snapshot` text,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_metadata_audit_system_id` (`system_id`,`id`),
  KEY `fk_metadata_audit_actor_system` (`system_id`,`actor_id`),
  CONSTRAINT `fk_metadata_audit_actor_system` FOREIGN KEY (`system_id`, `actor_id`) REFERENCES `sys_user` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_metadata_audit_system` FOREIGN KEY (`system_id`) REFERENCES `sys_system` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mdm_object_type` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `system_id` bigint NOT NULL,
  `code` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `status` varchar(32) NOT NULL,
  `department_scoped` tinyint(1) NOT NULL DEFAULT '1',
  `approval_required` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL,
  `updated_by` bigint DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_object_type_code` (`system_id`,`code`),
  UNIQUE KEY `uk_object_type_system_id` (`system_id`,`id`),
  KEY `fk_object_type_created_by` (`created_by`),
  KEY `fk_object_type_updated_by` (`updated_by`),
  KEY `fk_object_type_created_by_system` (`system_id`,`created_by`),
  KEY `fk_object_type_updated_by_system` (`system_id`,`updated_by`),
  CONSTRAINT `fk_object_type_created_by` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_object_type_created_by_system` FOREIGN KEY (`system_id`, `created_by`) REFERENCES `sys_user` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_object_type_system` FOREIGN KEY (`system_id`) REFERENCES `sys_system` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_object_type_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_object_type_updated_by_system` FOREIGN KEY (`system_id`, `updated_by`) REFERENCES `sys_user` (`system_id`, `id`) ON DELETE RESTRICT
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mdm_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `system_id` bigint NOT NULL,
  `object_type_id` bigint NOT NULL,
  `department_id` bigint NOT NULL,
  `record_code` varchar(128) NOT NULL,
  `status` varchar(32) NOT NULL,
  `approval_status` varchar(32) NOT NULL,
  `created_at` datetime NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL,
  `updated_by` bigint DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `deleted_at` datetime DEFAULT NULL,
  `active_record_code` varchar(128) GENERATED ALWAYS AS ((case when (`deleted_at` is null) then `record_code` else NULL end)) VIRTUAL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_record_system_id` (`system_id`,`id`),
  UNIQUE KEY `uk_record_code` (`object_type_id`,`active_record_code`),
  KEY `fk_record_department` (`department_id`),
  KEY `fk_record_created_by` (`created_by`),
  KEY `fk_record_updated_by` (`updated_by`),
  KEY `fk_record_object_system` (`system_id`,`object_type_id`),
  KEY `fk_record_department_system` (`system_id`,`department_id`),
  KEY `fk_record_created_by_system` (`system_id`,`created_by`),
  KEY `fk_record_updated_by_system` (`system_id`,`updated_by`),
  CONSTRAINT `fk_record_created_by` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_record_created_by_system` FOREIGN KEY (`system_id`, `created_by`) REFERENCES `sys_user` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_record_department` FOREIGN KEY (`department_id`) REFERENCES `sys_department` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_record_department_system` FOREIGN KEY (`system_id`, `department_id`) REFERENCES `sys_department` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_record_object_system` FOREIGN KEY (`system_id`, `object_type_id`) REFERENCES `mdm_object_type` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_record_object_type` FOREIGN KEY (`object_type_id`) REFERENCES `mdm_object_type` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_record_system` FOREIGN KEY (`system_id`) REFERENCES `sys_system` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_record_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_record_updated_by_system` FOREIGN KEY (`system_id`, `updated_by`) REFERENCES `sys_user` (`system_id`, `id`) ON DELETE RESTRICT
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mdm_record_value` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `record_id` bigint NOT NULL,
  `field_definition_id` bigint NOT NULL,
  `string_value` varchar(4096) DEFAULT NULL,
  `text_value` text,
  `integer_value` bigint DEFAULT NULL,
  `decimal_value` decimal(38,10) DEFAULT NULL,
  `boolean_value` tinyint(1) DEFAULT NULL,
  `date_value` date DEFAULT NULL,
  `datetime_value` datetime DEFAULT NULL,
  `reference_record_id` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL,
  `updated_by` bigint DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `system_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_record_field` (`record_id`,`field_definition_id`),
  KEY `fk_record_value_field` (`field_definition_id`),
  KEY `fk_record_value_reference` (`reference_record_id`),
  KEY `fk_record_value_created_by` (`created_by`),
  KEY `fk_record_value_updated_by` (`updated_by`),
  KEY `fk_record_value_record_system` (`system_id`,`record_id`),
  KEY `fk_record_value_field_system` (`system_id`,`field_definition_id`),
  KEY `fk_record_value_reference_system` (`system_id`,`reference_record_id`),
  KEY `fk_record_value_created_system` (`system_id`,`created_by`),
  KEY `fk_record_value_updated_system` (`system_id`,`updated_by`),
  CONSTRAINT `fk_record_value_created_by` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_record_value_created_system` FOREIGN KEY (`system_id`, `created_by`) REFERENCES `sys_user` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_record_value_field` FOREIGN KEY (`field_definition_id`) REFERENCES `mdm_field_definition` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_record_value_field_system` FOREIGN KEY (`system_id`, `field_definition_id`) REFERENCES `mdm_field_definition` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_record_value_record` FOREIGN KEY (`record_id`) REFERENCES `mdm_record` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_record_value_record_system` FOREIGN KEY (`system_id`, `record_id`) REFERENCES `mdm_record` (`system_id`, `id`) ON DELETE CASCADE,
  CONSTRAINT `fk_record_value_reference` FOREIGN KEY (`reference_record_id`) REFERENCES `mdm_record` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_record_value_reference_system` FOREIGN KEY (`system_id`, `reference_record_id`) REFERENCES `mdm_record` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_record_value_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_record_value_updated_system` FOREIGN KEY (`system_id`, `updated_by`) REFERENCES `sys_user` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_record_value_one_type` CHECK ((((((((((case when (`string_value` is not null) then 1 else 0 end) + (case when (`text_value` is not null) then 1 else 0 end)) + (case when (`integer_value` is not null) then 1 else 0 end)) + (case when (`decimal_value` is not null) then 1 else 0 end)) + (case when (`boolean_value` is not null) then 1 else 0 end)) + (case when (`date_value` is not null) then 1 else 0 end)) + (case when (`datetime_value` is not null) then 1 else 0 end)) + (case when (`reference_record_id` is not null) then 1 else 0 end)) <= 1))
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_approver_assignment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `system_id` bigint NOT NULL,
  `object_type_id` bigint DEFAULT NULL,
  `department_id` bigint NOT NULL,
  `approver_user_id` bigint NOT NULL,
  `status` varchar(32) NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_approver_assignment` (`system_id`,`object_type_id`,`department_id`,`approver_user_id`),
  KEY `fk_approver_type` (`object_type_id`),
  KEY `fk_approver_department` (`department_id`),
  KEY `fk_approver_user` (`approver_user_id`),
  KEY `fk_approver_department_system` (`system_id`,`department_id`),
  KEY `fk_approver_user_system` (`system_id`,`approver_user_id`),
  CONSTRAINT `fk_approver_department` FOREIGN KEY (`department_id`) REFERENCES `sys_department` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_approver_department_system` FOREIGN KEY (`system_id`, `department_id`) REFERENCES `sys_department` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_approver_object_system` FOREIGN KEY (`system_id`, `object_type_id`) REFERENCES `mdm_object_type` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_approver_system` FOREIGN KEY (`system_id`) REFERENCES `sys_system` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_approver_type` FOREIGN KEY (`object_type_id`) REFERENCES `mdm_object_type` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_approver_user` FOREIGN KEY (`approver_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_approver_user_system` FOREIGN KEY (`system_id`, `approver_user_id`) REFERENCES `sys_user` (`system_id`, `id`) ON DELETE RESTRICT
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_department` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `system_id` bigint NOT NULL,
  `parent_id` bigint DEFAULT NULL,
  `code` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `level` int NOT NULL,
  `path` varchar(2048) NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `status` varchar(32) NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_department_code` (`system_id`,`code`),
  UNIQUE KEY `uk_department_system_id` (`system_id`,`id`),
  UNIQUE KEY `uk_department_parent_name` (`system_id`,`parent_id`,`name`),
  KEY `fk_department_parent` (`parent_id`),
  CONSTRAINT `fk_department_parent` FOREIGN KEY (`parent_id`) REFERENCES `sys_department` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_department_parent_system` FOREIGN KEY (`system_id`, `parent_id`) REFERENCES `sys_department` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_department_system` FOREIGN KEY (`system_id`) REFERENCES `sys_system` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(128) NOT NULL,
  `name` varchar(128) NOT NULL,
  `description` varchar(512) DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_push_endpoint` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `system_id` bigint NOT NULL,
  `code` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `endpoint_url` varchar(2048) NOT NULL,
  `authentication_type` varchar(32) NOT NULL,
  `encrypted_credentials` varchar(4096) DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `schedule_enabled` tinyint(1) NOT NULL DEFAULT '0',
  `schedule_cron` varchar(128) DEFAULT NULL,
  `schedule_timezone` varchar(64) DEFAULT NULL,
  `schedule_next_at` datetime DEFAULT NULL,
  `schedule_last_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_push_endpoint_code` (`system_id`,`code`),
  UNIQUE KEY `uk_push_endpoint_system_id` (`system_id`,`id`),
  CONSTRAINT `fk_push_endpoint_system` FOREIGN KEY (`system_id`) REFERENCES `sys_system` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_push_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `system_id` bigint NOT NULL,
  `subscription_id` bigint NOT NULL,
  `record_id` bigint DEFAULT NULL,
  `event_id` varchar(128) NOT NULL,
  `status` varchar(32) NOT NULL,
  `retry_count` int NOT NULL DEFAULT '0',
  `request_snapshot` mediumtext,
  `response_snapshot` text,
  `last_attempt_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `trigger_type` varchar(16) NOT NULL DEFAULT 'AUTOMATIC',
  `triggered_by` bigint DEFAULT NULL,
  `trigger_reason` varchar(512) DEFAULT NULL,
  `last_retry_by` bigint DEFAULT NULL,
  `last_retry_reason` varchar(512) DEFAULT NULL,
  `last_retry_at` datetime DEFAULT NULL,
  `idempotency_key` varchar(160) NOT NULL,
  `active_dedup_key` varchar(160) DEFAULT NULL,
  `cancelled_by` bigint DEFAULT NULL,
  `cancelled_at` datetime DEFAULT NULL,
  `cancellation_reason` varchar(512) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_push_log_event` (`subscription_id`,`event_id`),
  UNIQUE KEY `uk_push_log_idempotency` (`idempotency_key`),
  UNIQUE KEY `uk_push_log_active_dedup` (`active_dedup_key`),
  KEY `fk_push_log_record` (`record_id`),
  KEY `fk_push_log_subscription_system` (`system_id`,`subscription_id`),
  KEY `fk_push_log_record_system` (`system_id`,`record_id`),
  KEY `fk_push_log_triggered_by_system` (`system_id`,`triggered_by`),
  KEY `fk_push_log_last_retry_by_system` (`system_id`,`last_retry_by`),
  KEY `fk_push_log_cancelled_by_system` (`system_id`,`cancelled_by`),
  CONSTRAINT `fk_push_log_cancelled_by_system` FOREIGN KEY (`system_id`, `cancelled_by`) REFERENCES `sys_user` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_push_log_last_retry_by_system` FOREIGN KEY (`system_id`, `last_retry_by`) REFERENCES `sys_user` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_push_log_record` FOREIGN KEY (`record_id`) REFERENCES `mdm_record` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_push_log_record_system` FOREIGN KEY (`system_id`, `record_id`) REFERENCES `mdm_record` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_push_log_subscription` FOREIGN KEY (`subscription_id`) REFERENCES `sys_push_subscription` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_push_log_subscription_system` FOREIGN KEY (`system_id`, `subscription_id`) REFERENCES `sys_push_subscription` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_push_log_triggered_by_system` FOREIGN KEY (`system_id`, `triggered_by`) REFERENCES `sys_user` (`system_id`, `id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_push_subscription` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `system_id` bigint NOT NULL,
  `endpoint_id` bigint NOT NULL,
  `object_type_id` bigint DEFAULT NULL,
  `event_type` varchar(64) NOT NULL,
  `status` varchar(32) NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_push_subscription_system_id` (`system_id`,`id`),
  UNIQUE KEY `uk_push_subscription` (`endpoint_id`,`object_type_id`,`event_type`),
  KEY `fk_push_subscription_type` (`object_type_id`),
  KEY `fk_subscription_endpoint_system` (`system_id`,`endpoint_id`),
  KEY `fk_subscription_object_system` (`system_id`,`object_type_id`),
  CONSTRAINT `fk_push_subscription_endpoint` FOREIGN KEY (`endpoint_id`) REFERENCES `sys_push_endpoint` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_push_subscription_system` FOREIGN KEY (`system_id`) REFERENCES `sys_system` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_push_subscription_type` FOREIGN KEY (`object_type_id`) REFERENCES `mdm_object_type` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_subscription_endpoint_system` FOREIGN KEY (`system_id`, `endpoint_id`) REFERENCES `sys_push_endpoint` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_subscription_object_system` FOREIGN KEY (`system_id`, `object_type_id`) REFERENCES `mdm_object_type` (`system_id`, `id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `system_id` bigint NOT NULL,
  `code` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `description` varchar(512) DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`system_id`,`code`),
  UNIQUE KEY `uk_role_system_id` (`system_id`,`id`),
  CONSTRAINT `fk_role_system` FOREIGN KEY (`system_id`) REFERENCES `sys_system` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_permission` (
  `role_id` bigint NOT NULL,
  `permission_id` bigint NOT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`role_id`,`permission_id`),
  KEY `fk_role_permission_permission` (`permission_id`),
  CONSTRAINT `fk_role_permission_permission` FOREIGN KEY (`permission_id`) REFERENCES `sys_permission` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_role_permission_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_system` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `status` varchar(32) NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_system_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `system_id` bigint NOT NULL,
  `department_id` bigint NOT NULL,
  `username` varchar(64) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `real_name` varchar(128) NOT NULL,
  `email` varchar(255) DEFAULT NULL,
  `mobile` varchar(64) DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `is_system_admin` tinyint(1) NOT NULL DEFAULT '0',
  `failed_login_count` int NOT NULL DEFAULT '0',
  `locked_until` datetime DEFAULT NULL,
  `last_login_at` datetime DEFAULT NULL,
  `password_changed_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL,
  `updated_by` bigint DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `deleted_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`system_id`,`username`),
  UNIQUE KEY `uk_user_system_id` (`system_id`,`id`),
  KEY `fk_user_department` (`department_id`),
  KEY `fk_user_created_by` (`created_by`),
  KEY `fk_user_updated_by` (`updated_by`),
  KEY `fk_user_department_system` (`system_id`,`department_id`),
  KEY `fk_user_created_by_system` (`system_id`,`created_by`),
  KEY `fk_user_updated_by_system` (`system_id`,`updated_by`),
  CONSTRAINT `fk_user_created_by` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_user_created_by_system` FOREIGN KEY (`system_id`, `created_by`) REFERENCES `sys_user` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_user_department` FOREIGN KEY (`department_id`) REFERENCES `sys_department` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_user_department_system` FOREIGN KEY (`system_id`, `department_id`) REFERENCES `sys_department` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_user_system` FOREIGN KEY (`system_id`) REFERENCES `sys_system` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_user_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_user_updated_by_system` FOREIGN KEY (`system_id`, `updated_by`) REFERENCES `sys_user` (`system_id`, `id`) ON DELETE RESTRICT
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_department_scope` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `system_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `department_id` bigint NOT NULL,
  `scope_mode` varchar(16) NOT NULL,
  `can_view` tinyint(1) NOT NULL DEFAULT '0',
  `can_edit` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_department_scope` (`user_id`,`department_id`,`scope_mode`),
  KEY `fk_scope_department` (`department_id`),
  KEY `fk_scope_user_system` (`system_id`,`user_id`),
  KEY `fk_scope_department_system` (`system_id`,`department_id`),
  CONSTRAINT `fk_scope_department` FOREIGN KEY (`department_id`) REFERENCES `sys_department` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_scope_department_system` FOREIGN KEY (`system_id`, `department_id`) REFERENCES `sys_department` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_scope_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_scope_user_system` FOREIGN KEY (`system_id`, `user_id`) REFERENCES `sys_user` (`system_id`, `id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_role` (
  `system_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`user_id`,`role_id`),
  KEY `fk_user_role_role` (`role_id`),
  KEY `fk_user_role_user_system` (`system_id`,`user_id`),
  KEY `fk_user_role_role_system` (`system_id`,`role_id`),
  CONSTRAINT `fk_user_role_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_user_role_role_system` FOREIGN KEY (`system_id`, `role_id`) REFERENCES `sys_role` (`system_id`, `id`) ON DELETE CASCADE,
  CONSTRAINT `fk_user_role_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_user_role_user_system` FOREIGN KEY (`system_id`, `user_id`) REFERENCES `sys_user` (`system_id`, `id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_approval_action` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `system_id` bigint NOT NULL,
  `approval_request_id` bigint NOT NULL,
  `actor_id` bigint NOT NULL,
  `action` varchar(32) NOT NULL,
  `comment` varchar(2048) DEFAULT NULL,
  `acted_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_approval_action_request` (`approval_request_id`),
  KEY `fk_approval_action_actor` (`actor_id`),
  KEY `fk_approval_action_request_system` (`system_id`,`approval_request_id`),
  KEY `fk_approval_action_actor_system` (`system_id`,`actor_id`),
  CONSTRAINT `fk_approval_action_actor` FOREIGN KEY (`actor_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_approval_action_actor_system` FOREIGN KEY (`system_id`, `actor_id`) REFERENCES `sys_user` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_approval_action_request` FOREIGN KEY (`approval_request_id`) REFERENCES `wf_approval_request` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_approval_action_request_system` FOREIGN KEY (`system_id`, `approval_request_id`) REFERENCES `wf_approval_request` (`system_id`, `id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_approval_change` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `approval_request_id` bigint NOT NULL,
  `field_definition_id` bigint NOT NULL,
  `old_string_value` varchar(4096) DEFAULT NULL,
  `old_text_value` text,
  `old_integer_value` bigint DEFAULT NULL,
  `old_decimal_value` decimal(38,10) DEFAULT NULL,
  `old_boolean_value` tinyint(1) DEFAULT NULL,
  `old_date_value` date DEFAULT NULL,
  `old_datetime_value` datetime DEFAULT NULL,
  `old_reference_record_id` bigint DEFAULT NULL,
  `new_string_value` varchar(4096) DEFAULT NULL,
  `new_text_value` text,
  `new_integer_value` bigint DEFAULT NULL,
  `new_decimal_value` decimal(38,10) DEFAULT NULL,
  `new_boolean_value` tinyint(1) DEFAULT NULL,
  `new_date_value` date DEFAULT NULL,
  `new_datetime_value` datetime DEFAULT NULL,
  `new_reference_record_id` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `system_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_approval_change_field` (`approval_request_id`,`field_definition_id`),
  KEY `fk_approval_change_field` (`field_definition_id`),
  KEY `fk_approval_change_old_ref` (`old_reference_record_id`),
  KEY `fk_approval_change_new_ref` (`new_reference_record_id`),
  KEY `fk_approval_change_request_system` (`system_id`,`approval_request_id`),
  KEY `fk_approval_change_field_system` (`system_id`,`field_definition_id`),
  KEY `fk_approval_change_old_ref_system` (`system_id`,`old_reference_record_id`),
  KEY `fk_approval_change_new_ref_system` (`system_id`,`new_reference_record_id`),
  CONSTRAINT `fk_approval_change_field` FOREIGN KEY (`field_definition_id`) REFERENCES `mdm_field_definition` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_approval_change_field_system` FOREIGN KEY (`system_id`, `field_definition_id`) REFERENCES `mdm_field_definition` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_approval_change_new_ref` FOREIGN KEY (`new_reference_record_id`) REFERENCES `mdm_record` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_approval_change_new_ref_system` FOREIGN KEY (`system_id`, `new_reference_record_id`) REFERENCES `mdm_record` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_approval_change_old_ref` FOREIGN KEY (`old_reference_record_id`) REFERENCES `mdm_record` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_approval_change_old_ref_system` FOREIGN KEY (`system_id`, `old_reference_record_id`) REFERENCES `mdm_record` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_approval_change_request` FOREIGN KEY (`approval_request_id`) REFERENCES `wf_approval_request` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_approval_change_request_system` FOREIGN KEY (`system_id`, `approval_request_id`) REFERENCES `wf_approval_request` (`system_id`, `id`) ON DELETE CASCADE,
  CONSTRAINT `ck_approval_change_new_one_type` CHECK ((((((((((case when (`new_string_value` is not null) then 1 else 0 end) + (case when (`new_text_value` is not null) then 1 else 0 end)) + (case when (`new_integer_value` is not null) then 1 else 0 end)) + (case when (`new_decimal_value` is not null) then 1 else 0 end)) + (case when (`new_boolean_value` is not null) then 1 else 0 end)) + (case when (`new_date_value` is not null) then 1 else 0 end)) + (case when (`new_datetime_value` is not null) then 1 else 0 end)) + (case when (`new_reference_record_id` is not null) then 1 else 0 end)) <= 1)),
  CONSTRAINT `ck_approval_change_old_one_type` CHECK ((((((((((case when (`old_string_value` is not null) then 1 else 0 end) + (case when (`old_text_value` is not null) then 1 else 0 end)) + (case when (`old_integer_value` is not null) then 1 else 0 end)) + (case when (`old_decimal_value` is not null) then 1 else 0 end)) + (case when (`old_boolean_value` is not null) then 1 else 0 end)) + (case when (`old_date_value` is not null) then 1 else 0 end)) + (case when (`old_datetime_value` is not null) then 1 else 0 end)) + (case when (`old_reference_record_id` is not null) then 1 else 0 end)) <= 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_approval_child_change` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `system_id` bigint NOT NULL,
  `approval_request_id` bigint NOT NULL,
  `change_key` varchar(128) NOT NULL,
  `child_type_id` bigint NOT NULL,
  `child_record_id` bigint DEFAULT NULL,
  `operation` varchar(16) NOT NULL,
  `expected_version` bigint DEFAULT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_approval_child_change_key` (`approval_request_id`,`change_key`),
  UNIQUE KEY `uk_approval_child_system_id` (`system_id`,`id`),
  KEY `fk_approval_child_request_system` (`system_id`,`approval_request_id`),
  KEY `fk_approval_child_type_system` (`system_id`,`child_type_id`),
  KEY `fk_approval_child_record_system` (`system_id`,`child_record_id`),
  CONSTRAINT `fk_approval_child_record_system` FOREIGN KEY (`system_id`, `child_record_id`) REFERENCES `mdm_child_record` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_approval_child_request_system` FOREIGN KEY (`system_id`, `approval_request_id`) REFERENCES `wf_approval_request` (`system_id`, `id`) ON DELETE CASCADE,
  CONSTRAINT `fk_approval_child_type_system` FOREIGN KEY (`system_id`, `child_type_id`) REFERENCES `mdm_child_type` (`system_id`, `id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_approval_child_value_change` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `system_id` bigint NOT NULL,
  `approval_child_change_id` bigint NOT NULL,
  `field_definition_id` bigint NOT NULL,
  `old_string_value` varchar(4096) DEFAULT NULL,
  `old_text_value` text,
  `old_integer_value` bigint DEFAULT NULL,
  `old_decimal_value` decimal(38,10) DEFAULT NULL,
  `old_boolean_value` tinyint(1) DEFAULT NULL,
  `old_date_value` date DEFAULT NULL,
  `old_datetime_value` datetime DEFAULT NULL,
  `old_reference_record_id` bigint DEFAULT NULL,
  `new_string_value` varchar(4096) DEFAULT NULL,
  `new_text_value` text,
  `new_integer_value` bigint DEFAULT NULL,
  `new_decimal_value` decimal(38,10) DEFAULT NULL,
  `new_boolean_value` tinyint(1) DEFAULT NULL,
  `new_date_value` date DEFAULT NULL,
  `new_datetime_value` datetime DEFAULT NULL,
  `new_reference_record_id` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_approval_child_value_field` (`approval_child_change_id`,`field_definition_id`),
  KEY `fk_approval_child_value_change_system` (`system_id`,`approval_child_change_id`),
  KEY `fk_approval_child_value_field_system` (`system_id`,`field_definition_id`),
  KEY `fk_approval_child_value_old_ref_system` (`system_id`,`old_reference_record_id`),
  KEY `fk_approval_child_value_new_ref_system` (`system_id`,`new_reference_record_id`),
  CONSTRAINT `fk_approval_child_value_change_system` FOREIGN KEY (`system_id`, `approval_child_change_id`) REFERENCES `wf_approval_child_change` (`system_id`, `id`) ON DELETE CASCADE,
  CONSTRAINT `fk_approval_child_value_field_system` FOREIGN KEY (`system_id`, `field_definition_id`) REFERENCES `mdm_child_field_definition` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_approval_child_value_new_ref_system` FOREIGN KEY (`system_id`, `new_reference_record_id`) REFERENCES `mdm_record` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_approval_child_value_old_ref_system` FOREIGN KEY (`system_id`, `old_reference_record_id`) REFERENCES `mdm_record` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_approval_child_value_new_one_type` CHECK ((((((((((case when (`new_string_value` is not null) then 1 else 0 end) + (case when (`new_text_value` is not null) then 1 else 0 end)) + (case when (`new_integer_value` is not null) then 1 else 0 end)) + (case when (`new_decimal_value` is not null) then 1 else 0 end)) + (case when (`new_boolean_value` is not null) then 1 else 0 end)) + (case when (`new_date_value` is not null) then 1 else 0 end)) + (case when (`new_datetime_value` is not null) then 1 else 0 end)) + (case when (`new_reference_record_id` is not null) then 1 else 0 end)) <= 1)),
  CONSTRAINT `ck_approval_child_value_old_one_type` CHECK ((((((((((case when (`old_string_value` is not null) then 1 else 0 end) + (case when (`old_text_value` is not null) then 1 else 0 end)) + (case when (`old_integer_value` is not null) then 1 else 0 end)) + (case when (`old_decimal_value` is not null) then 1 else 0 end)) + (case when (`old_boolean_value` is not null) then 1 else 0 end)) + (case when (`old_date_value` is not null) then 1 else 0 end)) + (case when (`old_datetime_value` is not null) then 1 else 0 end)) + (case when (`old_reference_record_id` is not null) then 1 else 0 end)) <= 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_approval_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `system_id` bigint NOT NULL,
  `object_type_id` bigint NOT NULL,
  `record_id` bigint DEFAULT NULL,
  `department_id` bigint NOT NULL,
  `requested_by` bigint NOT NULL,
  `expected_version` bigint DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `submitted_at` datetime NOT NULL,
  `decided_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `operation` varchar(16) NOT NULL DEFAULT 'UPDATE',
  `record_code` varchar(128) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_approval_request_system_id` (`system_id`,`id`),
  KEY `fk_approval_request_type` (`object_type_id`),
  KEY `fk_approval_request_record` (`record_id`),
  KEY `fk_approval_request_department` (`department_id`),
  KEY `fk_approval_request_user` (`requested_by`),
  KEY `fk_approval_object_system` (`system_id`,`object_type_id`),
  KEY `fk_approval_department_system` (`system_id`,`department_id`),
  KEY `fk_approval_record_system` (`system_id`,`record_id`),
  KEY `fk_approval_user_system` (`system_id`,`requested_by`),
  CONSTRAINT `fk_approval_department_system` FOREIGN KEY (`system_id`, `department_id`) REFERENCES `sys_department` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_approval_object_system` FOREIGN KEY (`system_id`, `object_type_id`) REFERENCES `mdm_object_type` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_approval_record_system` FOREIGN KEY (`system_id`, `record_id`) REFERENCES `mdm_record` (`system_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_approval_request_department` FOREIGN KEY (`department_id`) REFERENCES `sys_department` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_approval_request_record` FOREIGN KEY (`record_id`) REFERENCES `mdm_record` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_approval_request_system` FOREIGN KEY (`system_id`) REFERENCES `sys_system` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_approval_request_type` FOREIGN KEY (`object_type_id`) REFERENCES `mdm_object_type` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_approval_request_user` FOREIGN KEY (`requested_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_approval_user_system` FOREIGN KEY (`system_id`, `requested_by`) REFERENCES `sys_user` (`system_id`, `id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
