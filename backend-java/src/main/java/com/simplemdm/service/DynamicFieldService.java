package com.simplemdm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.MdmFieldDefinition;
import com.simplemdm.repository.MdmFieldDefinitionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DynamicFieldService {

    public record ValidationResult(Map<String, Object> data) {}

    private final MdmFieldDefinitionRepository fieldRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DynamicFieldService(MdmFieldDefinitionRepository fieldRepository) {
        this.fieldRepository = fieldRepository;
    }

    public ValidationResult validate(String systemCode, String department, String tableType,
                                     String subType, Map<String, Object> input) {
        Map<String, Object> safeInput = input == null ? Map.of() : input;
        List<MdmFieldDefinition> definitions = "master".equals(tableType)
            ? fieldRepository.findBySystemCodeAndTableTypeOrderBySubTypeAscSortOrderAsc(systemCode, "master")
            : fieldRepository.findBySystemCodeAndDepartmentAndTableTypeAndSubTypeOrderBySortOrderAsc(
                systemCode, department, "sub", subType);

        Map<String, MdmFieldDefinition> byKey = definitions.stream()
            .filter(field -> !Boolean.TRUE.equals(field.getSystemField()))
            .collect(Collectors.toMap(MdmFieldDefinition::getFieldKey, Function.identity(),
                (left, right) -> left, LinkedHashMap::new));

        for (String key : safeInput.keySet()) {
            if (!byKey.containsKey(key)) {
                throw new BusinessException(400, "未知字段: " + key);
            }
        }

        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        for (MdmFieldDefinition definition : definitions) {
            if (Boolean.TRUE.equals(definition.getSystemField())) continue;
            String key = definition.getFieldKey();
            Object value = safeInput.get(key);
            if (isBlank(value)) {
                if (Boolean.TRUE.equals(definition.getRequired())) {
                    throw new BusinessException(400, "字段 " + key + " 为必填项");
                }
                continue;
            }
            normalized.put(key, normalize(definition, value));
        }
        return new ValidationResult(normalized);
    }

    public Map<String, Object> computeDiff(Map<String, Object> oldData, Map<String, Object> newData) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (oldData != null) keys.addAll(oldData.keySet());
        if (newData != null) keys.addAll(newData.keySet());
        for (String key : keys) {
            Object oldValue = oldData == null ? null : oldData.get(key);
            Object newValue = newData == null ? null : newData.get(key);
            if (!Objects.equals(oldValue, newValue)) {
                LinkedHashMap<String, Object> change = new LinkedHashMap<>();
                change.put("old", oldValue);
                change.put("new", newValue);
                result.put(key, change);
            }
        }
        return result;
    }

    private Object normalize(MdmFieldDefinition definition, Object value) {
        String key = definition.getFieldKey();
        try {
            return switch (definition.getFieldType()) {
                case "number" -> new BigDecimal(value.toString());
                case "date" -> LocalDate.parse(value.toString()).toString();
                case "select", "radio" -> validateOption(definition, value.toString());
                default -> value.toString();
            };
        } catch (NumberFormatException exception) {
            throw new BusinessException(400, "字段 " + key + " 必须是数字");
        } catch (DateTimeParseException exception) {
            throw new BusinessException(400, "字段 " + key + " 必须是日期 yyyy-MM-dd");
        }
    }

    private String validateOption(MdmFieldDefinition definition, String value) {
        try {
            List<String> options = definition.getOptionsJson() == null
                ? List.of()
                : objectMapper.readValue(definition.getOptionsJson(), new TypeReference<>() {});
            if (!options.contains(value)) {
                throw new BusinessException(400, "字段 " + definition.getFieldKey() + " 的值不在允许选项中");
            }
            return value;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(500, "字段 " + definition.getFieldKey() + " 的选项配置无效");
        }
    }

    private boolean isBlank(Object value) {
        return value == null || (value instanceof String text && text.trim().isEmpty());
    }
}
