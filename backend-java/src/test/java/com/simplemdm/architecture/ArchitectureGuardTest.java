package com.simplemdm.architecture;
import static org.assertj.core.api.Assertions.assertThat;
import java.io.IOException;import java.nio.file.*;import java.util.List;import org.junit.jupiter.api.Test;
class ArchitectureGuardTest {
 private static final Path PRODUCTION_SOURCE=Path.of("src","main","java","com","simplemdm");
 @Test void noLegacyPersonnelPersistenceOrRuntimePathsRemain()throws IOException{try(var files=Files.walk(PRODUCTION_SOURCE)){assertThat(files.filter(Files::isRegularFile).filter(x->x.toString().endsWith(".java")).flatMap(x->violations(x.toString(),read(x)).stream()).toList()).isEmpty();}}
 @Test void noLegacyFrontendRoutesOrImportsRemain()throws IOException{Path front=Path.of("..","frontend","src");try(var files=Files.walk(front)){assertThat(files.filter(Files::isRegularFile).filter(x->!x.toString().endsWith(".spec.js")).flatMap(x->frontendViolations(x.toString(),read(x)).stream()).toList()).isEmpty();}}
 @Test void detectsLegacyRelativePathAndBareDeclarationTokens(){assertThat(violations("fixture/model/MdmFieldDefinition.java","package fixture.model; public class MdmFieldDefinition {}")).contains("fixture/model/MdmFieldDefinition.java -> model/MdmFieldDefinition.java","fixture/model/MdmFieldDefinition.java -> class MdmFieldDefinition");}
 static List<String> violations(String path,String source){return List.of("model/MdmFieldDefinition.java","class MdmFieldDefinition","interface MdmFieldDefinition","record MdmFieldDefinition","MdmPersonnel","PersonnelSubService","PersonnelService","DynamicFieldService","owner_dept","mdm_personnel","dynamic_data","com.simplemdm.model.MdmFieldDefinition","PersonnelController","MdmPersonnelRepository","DynamicPersonnelDTO").stream().filter(t->path.replace('\\','/').contains(t)||source.contains(t)).map(t->path+" -> "+t).toList();}
 static List<String> frontendViolations(String path,String source){return List.of("/personnel","views/personnel","dept-fields","api/personnel","api/deptFields","router.push('/dashboard')").stream().filter(source::contains).map(t->path+" -> "+t).toList();}
 private static String read(Path p){try{return Files.readString(p);}catch(IOException e){throw new IllegalStateException(e);}}
}