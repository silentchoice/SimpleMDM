package com.example.mdm.metadata;

import com.example.mdm.common.error.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
class JdbcMetadataRepository implements MetadataRepository {
  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper json;
  JdbcMetadataRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper json) { this.jdbc=jdbc; this.json=json; }

  @Override public MasterType createMasterType(String code,String name,long actorId) {
    var key=new GeneratedKeyHolder();
    try {
      jdbc.update("INSERT INTO master_types(code,name,status,created_by) VALUES(:code,:name,'ACTIVE',:actor)",
          new MapSqlParameterSource(Map.of("code",code,"name",name,"actor",actorId)),key);
    } catch (DuplicateKeyException exception) {
      throw metadataConflict(exception);
    } catch (DataIntegrityViolationException exception) {
      throw metadataConflict(exception);
    }
    return new MasterType(key.getKey().longValue(),code,name,MetadataStatus.ACTIVE);
  }
  @Override public void assignDepartment(long departmentId,long masterTypeId) {
    try {
      jdbc.update("INSERT INTO department_master_types(department_id,master_type_id,status) VALUES(:department,:type,'ACTIVE')",
          Map.of("department",departmentId,"type",masterTypeId));
    } catch (DuplicateKeyException exception) {
      throw metadataConflict(exception);
    } catch (DataIntegrityViolationException exception) {
      throw metadataConflict(exception);
    }
  }
  @Override public void requireAssignment(long departmentId,long masterTypeId) {
    Integer assignments=jdbc.queryForObject("SELECT COUNT(*) FROM department_master_types "
        +"WHERE department_id=:department AND master_type_id=:type AND status='ACTIVE'",
        Map.of("department",departmentId,"type",masterTypeId),Integer.class);
    if (assignments==null || assignments==0) throw BusinessException.notFound("Master type assignment");
  }
  @Override public void requireTemplateAccess(long departmentId,long masterTypeId) {
    Integer assignments=jdbc.queryForObject("SELECT COUNT(*) FROM department_master_types "
        +"WHERE department_id=:department AND master_type_id=:type AND status='ACTIVE'",
        Map.of("department",departmentId,"type",masterTypeId),Integer.class);
    if (assignments!=null && assignments>0) return;
    rejectForeignOrMissingTemplate(masterTypeId);
  }
  @Override public void lockTemplateAssignment(long departmentId,long masterTypeId) {
    var assignments=jdbc.query("SELECT master_type_id FROM department_master_types "
            +"WHERE department_id=:department AND master_type_id=:type AND status='ACTIVE' FOR UPDATE",
        Map.of("department",departmentId,"type",masterTypeId),(rs,n)->rs.getLong("master_type_id"));
    if (assignments.isEmpty()) rejectForeignOrMissingTemplate(masterTypeId);
  }
  private void rejectForeignOrMissingTemplate(long masterTypeId) {
    Integer templates=jdbc.queryForObject("SELECT COUNT(*) FROM master_types WHERE id=:type AND status='ACTIVE'",
        Map.of("type",masterTypeId),Integer.class);
    if (templates!=null && templates>0) throw BusinessException.forbidden();
    throw BusinessException.notFound("Master type");
  }
  @Override public FieldDefinition createMasterField(long departmentId,FieldDefinition field) {
    requireAssignment(departmentId,field.ownerTypeId());
    return createField(departmentId,"master_fields","master_type_id",field);
  }
  @Override public SubType createSubType(long departmentId,long masterTypeId,String code,String name) {
    requireAssignment(departmentId,masterTypeId);
    var key=new GeneratedKeyHolder();
    Integer position=jdbc.queryForObject("SELECT COALESCE(MAX(sort_order),-1)+1 FROM sub_types "
            +"WHERE department_id=:department AND master_type_id=:owner",
        Map.of("department",departmentId,"owner",masterTypeId),Integer.class);
    try {
      jdbc.update("INSERT INTO sub_types(department_id,master_type_id,code,name,sort_order,status) "
              +"VALUES(:department,:owner,:code,:name,:position,'ACTIVE')",
          new MapSqlParameterSource(Map.of("department",departmentId,"owner",masterTypeId,
              "code",code,"name",name,"position",position==null?0:position)),key);
    } catch (DuplicateKeyException exception) {
      throw metadataConflict(exception);
    } catch (DataIntegrityViolationException exception) {
      throw metadataConflict(exception);
    }
    return new SubType(key.getKey().longValue(),masterTypeId,code,name,MetadataStatus.ACTIVE);
  }
  @Override public FieldDefinition createSubField(long departmentId,FieldDefinition field) {
    requireSubType(departmentId,field.ownerTypeId());
    return createField(departmentId,"sub_fields","sub_type_id",field);
  }
  private FieldDefinition createField(long departmentId,String table,String ownerColumn,FieldDefinition field) {
    var key=new GeneratedKeyHolder();
    String shared = table.equals("sub_fields") ? ",share_config" : "";
    String sharedValue = table.equals("sub_fields") ? ",:shared" : "";
    var params=new MapSqlParameterSource().addValue("department",departmentId).addValue("owner",field.ownerTypeId()).addValue("code",field.code())
        .addValue("name",field.displayName()).addValue("type",field.fieldType().name())
        .addValue("required",field.required()).addValue("options",writeOptions(field.options()))
        .addValue("shared",field.shared()).addValue("sort",field.sortOrder());
    try {
      jdbc.update("INSERT INTO "+table+"(department_id,"+ownerColumn+",code,display_name,field_type,required_flag,options,sort_order,status"+shared+") "
          +"VALUES(:department,:owner,:code,:name,:type,:required,:options,:sort,'ACTIVE'"+sharedValue+")",params,key);
    } catch (DuplicateKeyException exception) {
      throw metadataConflict(exception);
    } catch (DataIntegrityViolationException exception) {
      throw metadataConflict(exception);
    }
    return new FieldDefinition(key.getKey().longValue(),field.ownerTypeId(),field.code(),field.displayName(),
        field.fieldType(),field.required(),field.options(),field.shared(),field.sortOrder(),MetadataStatus.ACTIVE);
  }
  @Override public List<MasterType> findMasterTypes() {
    return jdbc.query("SELECT id,code,name,status FROM master_types ORDER BY id",Map.of(),(rs,n)->
        new MasterType(rs.getLong("id"),rs.getString("code"),rs.getString("name"),MetadataStatus.valueOf(rs.getString("status"))));
  }
  @Override public MasterType findAssignedMasterType(long departmentId) {
    var assignments = jdbc.query("SELECT mt.id,mt.code,mt.name,mt.status FROM department_master_types dmt "
        +"JOIN master_types mt ON mt.id=dmt.master_type_id WHERE dmt.department_id=:department "
        +"AND dmt.status='ACTIVE' AND mt.status='ACTIVE' ORDER BY mt.id",
        Map.of("department", departmentId), (rs,n) -> new MasterType(rs.getLong("id"),
            rs.getString("code"), rs.getString("name"), MetadataStatus.valueOf(rs.getString("status"))));
    if (assignments.isEmpty()) throw BusinessException.notFound("Master type assignment");
    return assignments.get(0);
  }
  @Override public List<FieldDefinition> findMasterFields(long departmentId,long masterTypeId) {
    return fields("master_fields","master_type_id",departmentId,masterTypeId,false);
  }
  @Override public List<SubType> findSubTypes(long departmentId,long masterTypeId) {
    return jdbc.query("SELECT id,master_type_id,code,name,status FROM sub_types WHERE department_id=:department "
        +"AND master_type_id=:id AND status='ACTIVE' ORDER BY sort_order,id",Map.of("department",departmentId,"id",masterTypeId),(rs,n)->
        new SubType(rs.getLong("id"),rs.getLong("master_type_id"),rs.getString("code"),rs.getString("name"),MetadataStatus.valueOf(rs.getString("status"))));
  }
  @Override public List<FieldDefinition> findSubFields(long departmentId,long subTypeId) {
    return fields("sub_fields","sub_type_id",departmentId,subTypeId,true);
  }
  @Override public void replaceMasterFields(long departmentId,long masterTypeId,List<FieldDefinition> fields) {
    try {
      jdbc.update("DELETE FROM master_fields WHERE department_id=:department AND master_type_id=:owner",
          Map.of("department",departmentId,"owner",masterTypeId));
      fields.forEach(field -> createField(departmentId,"master_fields","master_type_id",field));
    } catch (DataIntegrityViolationException exception) { throw metadataConflict(exception); }
  }
  @Override public void replaceSubTypes(long departmentId,long masterTypeId,List<SubType> types) {
    var existingById = new LinkedHashMap<Long, SubType>();
    var existingByCode = new LinkedHashMap<String, SubType>();
    findSubTypes(departmentId,masterTypeId).forEach(type -> {
      existingById.put(type.id(),type);
      existingByCode.put(type.code().toLowerCase(java.util.Locale.ROOT),type);
    });
    var retained = new java.util.HashSet<Long>();
    try {
      for (int position=0;position<types.size();position++) {
        var desired=types.get(position);
        SubType existing = desired.id()>0 ? existingById.get(desired.id())
            : existingByCode.get(desired.code().toLowerCase(java.util.Locale.ROOT));
        if (desired.id()>0 && (existing==null || !existing.code().equalsIgnoreCase(desired.code()))) {
          throw new BusinessException(HttpStatus.CONFLICT,"Metadata subtype identity conflict");
        }
        if (existing==null) {
          insertSubType(departmentId,masterTypeId,desired.code(),desired.name(),position);
        } else {
          int updated=jdbc.update("UPDATE sub_types SET name=:name,sort_order=:position,status='ACTIVE' WHERE "
                  +"id=:id AND department_id=:department AND master_type_id=:owner AND code=:code",
              Map.of("name",desired.name(),"id",existing.id(),"department",departmentId,
                  "owner",masterTypeId,"code",existing.code(),"position",position));
          if (updated!=1) throw new BusinessException(HttpStatus.CONFLICT,"Metadata subtype changed");
          retained.add(existing.id());
        }
      }
      for (var existing : existingById.values()) {
        if (!retained.contains(existing.id())) removeSubType(departmentId,masterTypeId,existing.id());
      }
    } catch (DataIntegrityViolationException exception) { throw metadataConflict(exception); }
  }
  @Override public void replaceSubFields(long departmentId,long subTypeId,List<FieldDefinition> fields) {
    try {
      jdbc.update("DELETE FROM sub_fields WHERE department_id=:department AND sub_type_id=:owner",
          Map.of("department",departmentId,"owner",subTypeId));
      fields.forEach(field -> createField(departmentId,"sub_fields","sub_type_id",field));
    } catch (DataIntegrityViolationException exception) { throw metadataConflict(exception); }
  }
  private void insertSubType(long departmentId,long masterTypeId,String code,String name,int position) {
    var key=new GeneratedKeyHolder();
    jdbc.update("INSERT INTO sub_types(department_id,master_type_id,code,name,sort_order,status) "
            +"VALUES(:department,:owner,:code,:name,:position,'ACTIVE')",
        new MapSqlParameterSource(Map.of("department",departmentId,"owner",masterTypeId,
            "code",code,"name",name,"position",position)),key);
  }
  private void removeSubType(long departmentId,long masterTypeId,long subTypeId) {
    var parameters=Map.<String,Object>of("department",departmentId,"owner",masterTypeId,"id",subTypeId);
    Integer dependencies=jdbc.queryForObject("SELECT "
        +"(SELECT COUNT(*) FROM sub_fields WHERE department_id=:department AND sub_type_id=:id)+"
        +"(SELECT COUNT(*) FROM sub_records WHERE sub_type_id=:id)+"
        +"(SELECT COUNT(*) FROM sub_record_drafts WHERE sub_type_id=:id)+"
        +"(SELECT COUNT(*) FROM approval_tasks WHERE department_id=:department "
        +"AND entity_type='SUB_FIELDS' AND entity_id=:id AND status='PENDING')",
        parameters,Integer.class);
    if (dependencies!=null && dependencies>0) {
      throw new BusinessException(HttpStatus.CONFLICT,"Subtype has dependent metadata or records");
    }
    int deleted=jdbc.update("DELETE FROM sub_types WHERE department_id=:department "
        +"AND master_type_id=:owner AND id=:id",parameters);
    if (deleted!=1) throw new BusinessException(HttpStatus.CONFLICT,"Metadata subtype changed");
  }
  private List<FieldDefinition> fields(String table,String owner,long departmentId,long id,boolean shared) {
    String sharedColumn=shared?",share_config":"";
    return jdbc.query("SELECT id,"+owner+",code,display_name,field_type,required_flag,options,sort_order,status"+sharedColumn+
        " FROM "+table+" WHERE department_id=:department AND "+owner+"=:id AND status='ACTIVE' ORDER BY sort_order,id",
        Map.of("department",departmentId,"id",id),(rs,n)->
        new FieldDefinition(rs.getLong("id"),rs.getLong(owner),rs.getString("code"),rs.getString("display_name"),
            FieldType.valueOf(rs.getString("field_type")),rs.getBoolean("required_flag"),readOptions(rs.getString("options")),
            shared&&rs.getBoolean("share_config"),rs.getInt("sort_order"),MetadataStatus.valueOf(rs.getString("status"))));
  }
  private void requireSubType(long departmentId,long subTypeId) {
    Integer subTypes=jdbc.queryForObject("SELECT COUNT(*) FROM sub_types WHERE department_id=:department AND id=:id "
        +"AND status='ACTIVE'",Map.of("department",departmentId,"id",subTypeId),Integer.class);
    if (subTypes==null || subTypes==0) throw BusinessException.notFound("Sub type");
  }
  private BusinessException metadataConflict(DataIntegrityViolationException exception) {
    return new BusinessException(HttpStatus.CONFLICT,"Metadata conflict");
  }
  private String writeOptions(List<String> options) { try{return options.isEmpty()?null:json.writeValueAsString(options);}catch(Exception e){throw new IllegalArgumentException(e);} }
  private List<String> readOptions(String options) { try{return options==null?List.of():json.readValue(options,new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException(e);} }
}
