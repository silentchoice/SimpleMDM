package com.simplemdm.config;

import com.simplemdm.model.mdm.*;
import com.simplemdm.model.system.*;
import com.simplemdm.repository.mdm.*;
import com.simplemdm.repository.system.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties={
 "spring.datasource.url=jdbc:h2:mem:bootstrap;MODE=MySQL;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
 "spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=validate",
 "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect","app.bootstrap.enabled=false"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackageClasses={SystemEntity.class,ObjectType.class})
@EnableJpaRepositories(basePackages={"com.simplemdm.repository.system","com.simplemdm.repository.mdm"})
@Import({DataInitializerTest.Config.class,BootstrapCoordinator.class})
class DataInitializerTest {
 @org.springframework.boot.test.context.TestConfiguration static class Config {
  @org.springframework.context.annotation.Bean BCryptPasswordEncoder encoder(){return new BCryptPasswordEncoder();}
 }
 @Autowired SystemRepository systems; @Autowired DepartmentRepository departments;
 @Autowired UserRepository users; @Autowired ObjectTypeRepository objects;
 @Autowired MdmRecordRepository records; @Autowired EntityManager em;
 @Autowired BCryptPasswordEncoder encoder;
 @Autowired BootstrapCoordinator coordinator;
 @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
 @Test void bootstrapIsIdempotentAndRelational() throws Exception {
  DataInitializer it=initializer(true); it.run(); it.run(); em.flush(); em.clear();
  SystemEntity system=systems.findByCode("DEFAULT").orElseThrow();
  assertThat(systems.count()).isEqualTo(1);
  assertThat(departments.findBySystem_Id(system.getId())).extracting(Department::getPath).doesNotContainNull();
  assertThat(users.findAll()).hasSize(1).allMatch(u->u.getDepartmentId()!=null&&u.getSystemId().equals(system.getId()));
  assertThat(records.findAll()).allMatch(r->r.getDepartmentId()!=null&&r.getSystemId().equals(system.getId()));
  assertThat(objects.findBySystemIdAndCode(system.getId(),"person")).isPresent();
  assertThat(count("sys_role")).isEqualTo(1); assertThat(count("sys_permission")).isGreaterThanOrEqualTo(4);
  assertThat(count("sys_user_role")).isEqualTo(1); assertThat(count("sys_role_permission")).isGreaterThanOrEqualTo(4);
  assertThat(count("sys_user_department_scope")).isEqualTo(1);
  assertThat(count("mdm_field_definition")).isGreaterThanOrEqualTo(3); assertThat(count("mdm_child_type")).isEqualTo(1);
 }
 @Test void disabledBootstrapCreatesNothing() throws Exception {initializer(false).run();assertThat(systems.count()).isZero();}
 @Test void bootstrapCompletesPartiallyExistingStableCodesWithoutOverwritingThem() throws Exception {
  SystemEntity system=systems.saveAndFlush(SystemEntity.create("DEFAULT","Existing Name"));
  Department root=departments.saveAndFlush(Department.create(system,null,"ROOT","Existing Root"));
  root.relocate(null,"/"+root.getId()+"/",1); departments.saveAndFlush(root);
  User admin=User.create(system,root,"admin",encoder.encode("keep-me"),"Existing Admin");
  admin.makeSystemAdmin();
  users.saveAndFlush(admin); String hash=password(admin.getId());
  initializer(true).run(); em.flush(); em.clear();
  assertThat(systems.count()).isEqualTo(1); assertThat(departments.count()).isEqualTo(3);
  assertThat(password(admin.getId())).isEqualTo(hash); assertThat(encoder.matches("keep-me",hash)).isTrue();
  assertThat(objects.findBySystemIdAndCode(system.getId(),"person")).isPresent();
  assertThat(count("sys_user_role")).isEqualTo(1); assertThat(count("mdm_child_type")).isEqualTo(1);
 }
 @Test void concurrentBootstrapCallsBothSucceedAndCreateOneStableGraph() throws Exception {
  var start=new java.util.concurrent.CountDownLatch(1);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
  java.util.concurrent.Callable<Void> call=()->{start.await();initializer(true).run();return null;};
  var first=pool.submit(call);var second=pool.submit(call);start.countDown();
  first.get(10,java.util.concurrent.TimeUnit.SECONDS);second.get(10,java.util.concurrent.TimeUnit.SECONDS);pool.shutdownNow();
  em.clear();assertThat(systems.count()).isEqualTo(1);assertThat(departments.count()).isEqualTo(3);
  assertThat(users.count()).isEqualTo(1);assertThat(count("sys_role")).isEqualTo(1);
  assertThat(count("sys_user_role")).isEqualTo(1);assertThat(count("mdm_field_definition")).isEqualTo(3);
  var cleanup=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
  cleanup.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  cleanup.executeWithoutResult(s->{em.createNativeQuery("delete from mdm_child_type").executeUpdate();
   em.createNativeQuery("delete from mdm_field_definition").executeUpdate();
   em.createNativeQuery("delete from mdm_object_type").executeUpdate();
   em.createNativeQuery("delete from sys_user_department_scope").executeUpdate();
   em.createNativeQuery("delete from sys_user_role").executeUpdate();em.createNativeQuery("delete from sys_role_permission").executeUpdate();
   em.createNativeQuery("delete from sys_permission").executeUpdate();em.createNativeQuery("delete from sys_role").executeUpdate();
   em.createNativeQuery("delete from sys_user").executeUpdate();em.createNativeQuery("delete from sys_department").executeUpdate();
   em.createNativeQuery("delete from sys_system").executeUpdate();});
 }
 @Test void conflictingStableDepartmentTopologyFailsFast() throws Exception {
  SystemEntity system=systems.saveAndFlush(SystemEntity.create("DEFAULT","Existing"));
  departments.saveAndFlush(Department.create(system,null,"HR","Wrong Root"));
  assertThatThrownBy(()->initializer(true).run()).isInstanceOf(IllegalStateException.class)
   .hasMessageContaining("HR").hasMessageContaining("parent");
 }
 @Test void conflictingStableDepartmentPathAndLevelFailFast() throws Exception {
  initializer(true).run();em.flush();
  em.createNativeQuery("update sys_department set path='/wrong/', level=9 where code='HR'").executeUpdate();
  em.flush();em.clear();
  assertThatThrownBy(()->initializer(true).run()).isInstanceOf(IllegalStateException.class)
   .hasMessageContaining("HR").hasMessageContaining("path/level");
 }
 @Test void conflictingStableFieldSemanticsFailsFast() throws Exception {
  initializer(true).run();em.flush();
  em.createNativeQuery("update mdm_field_definition set data_type='INTEGER' where field_key='employee_code'")
   .executeUpdate();em.flush();em.clear();
  assertThatThrownBy(()->initializer(true).run()).isInstanceOf(IllegalStateException.class)
   .hasMessageContaining("employee_code").hasMessageContaining("semantics");
 }
 @Test void inactiveStableFieldFailsFast() throws Exception {
  assertFieldMutationFails("status='inactive'","employee_code");
 }
 @Test void unsearchableStableFieldFailsFast() throws Exception {
  assertFieldMutationFails("searchable=false","employee_name");
 }
 @Test void wrongStableFieldSortOrderFailsFast() throws Exception {
  assertFieldMutationFails("sort_order=99","work_email");
 }
 @Test void inactiveStableChildTypeFailsFast() throws Exception {
  initializer(true).run();em.flush();
  em.createNativeQuery("update mdm_child_type set status='inactive' where code='employment'").executeUpdate();
  em.flush();em.clear();
  assertThatThrownBy(()->initializer(true).run()).isInstanceOf(IllegalStateException.class)
   .hasMessageContaining("employment").hasMessageContaining("semantics");
 }
 @Test void restartPreservesPasswordAndBusinessRecords() throws Exception {
  DataInitializer it=initializer(true);it.run();em.flush();
  SystemEntity s=systems.findByCode("DEFAULT").orElseThrow(); User admin=users.findBySystemIdAndUsername(s.getId(),"admin").orElseThrow();
  String hash=password(admin.getId()); Department root=departments.findBySystemIdAndCode(s.getId(),"ROOT").orElseThrow();
  ObjectType person=objects.findBySystemIdAndCode(s.getId(),"person").orElseThrow();
  records.saveAndFlush(MdmRecord.create(s.getId(),person,person.getId(),root,"EXISTING",admin.getId()));
  it.run();em.flush();em.clear();
  assertThat(password(admin.getId())).isEqualTo(hash);assertThat(encoder.matches("123456",hash)).isTrue();
  assertThat(records.findAll()).extracting(MdmRecord::getRecordCode).containsExactly("EXISTING");
 }
 private DataInitializer initializer(boolean enabled){return new DataInitializer(em,systems,departments,users,objects,coordinator,enabled,encoder);}
 private void assertFieldMutationFails(String assignment,String key) throws Exception {
  initializer(true).run();em.flush();
  em.createNativeQuery("update mdm_field_definition set "+assignment+" where field_key=:key")
   .setParameter("key",key).executeUpdate();em.flush();em.clear();
  assertThatThrownBy(()->initializer(true).run()).isInstanceOf(IllegalStateException.class)
   .hasMessageContaining(key).hasMessageContaining("semantics");
 }
 private long count(String table){return ((Number)em.createNativeQuery("select count(*) from "+table).getSingleResult()).longValue();}
 private String password(Long id){return (String)em.createNativeQuery("select password_hash from sys_user where id=:id").setParameter("id",id).getSingleResult();}
}
