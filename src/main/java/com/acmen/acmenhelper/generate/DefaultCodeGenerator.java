package com.acmen.acmenhelper.generate;

import com.acmen.acmenhelper.common.RequestHolder;
import com.acmen.acmenhelper.model.CodeDefinition;
import com.acmen.acmenhelper.model.CodeDefinitionDetail;
import com.acmen.acmenhelper.model.DBDefinition;
import com.google.common.collect.Maps;
import freemarker.template.TemplateExceptionHandler;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.generator.api.MyBatisGenerator;
import org.mybatis.generator.config.*;
import org.mybatis.generator.internal.DefaultShellCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.acmen.acmenhelper.model.CodeDefinitionDetail.*;
import static com.acmen.acmenhelper.util.NameConvertUtil.*;

/**
 * 默认代码生成器，SOA单体架构
 * @author gaowenfeng
 * @date 2018/5/16
 *
 * TODO 待测试
 */
@Service("defaultCodeGenerator")
@Scope("prototype")
@Slf4j
//TODO 测试用最后删掉
@Data
public class DefaultCodeGenerator extends AbstractCodeGenerator {

    private final static String LOG_PRE = "代码生成器>";

    @Autowired
    private freemarker.template.Configuration cfg;

    private Context context;

    @Override
    public void setCodeDefinitionDetail(CodeDefinitionDetail codeDefinitionDetail) {
        super.setCodeDefinitionDetail(codeDefinitionDetail);
        this.context = getContext();
    }

    @Override
    protected void genConfigCode() {
        CodeDefinitionDetail codeDefinitionDetail = this.codeDefinitionDetail;
        DBDefinition dbDefinition = getDbDefinitionFromSession();
        Map<String, Object> data = buildConfigDataMap(codeDefinitionDetail, dbDefinition);

        File ymlDevFile = new File(codeDefinitionDetail.getProjectPath() + RESOURCES_PATH + "/application-dev.yml");
        generateFtlCode(data,ymlDevFile,"application-dev.ftl");

        File ymlFile = new File(codeDefinitionDetail.getProjectPath() + RESOURCES_PATH + "/application.yml");
        generateFtlCode(data,ymlFile,"application.ftl");

        File mybatisConfigFile = new File(codeDefinitionDetail.getProjectPath() + JAVA_PATH + codeDefinitionDetail.getCorePackage() + "/MybatisConfigurator.java");
        generateFtlCode(data,mybatisConfigFile,"MybatisConfigurator.ftl");
    }

    @Override
    protected void genFtlCode(String tableName, String modelName) {
        String modelNameUpperCamel = buildModelNameUpperCamel(tableName, modelName);

        log.info(LOG_PRE+modelNameUpperCamel+"-controller/service/impl生成开始");
        try {
            CodeDefinitionDetail codeDefinitionDetail = super.codeDefinitionDetail;

            //构建占位符数据
            Map<String,Object> data = buildCrudDataMap(modelNameUpperCamel,tableName);

            //生成java类
            File controllerFile = new File(codeDefinitionDetail.getProjectPath() + JAVA_PATH + codeDefinitionDetail.getControllerPackage() + modelNameUpperCamel + "Controller.java");
            generateFtlCode(data,controllerFile,"controller.ftl");

            //生成server类
            File serviceFile = new File(codeDefinitionDetail.getProjectPath() + JAVA_PATH + codeDefinitionDetail.getServicePackage() + modelNameUpperCamel + "Service.java");
            generateFtlCode(data,serviceFile,"service.ftl");

            //生成server.impl类
            File serviceImplFile = new File(codeDefinitionDetail.getProjectPath() + JAVA_PATH + codeDefinitionDetail.getServiceImplPackage() + modelNameUpperCamel + "ServiceImpl.java");
            generateFtlCode(data,serviceImplFile,"service-impl.ftl");

            log.info(LOG_PRE+modelNameUpperCamel+"-controller/service/impl生成成功");
        } catch (Exception e) {
            //TODO 自定义异常
            throw new RuntimeException("生成controller/service/impl失败",e);
        }
    }

    @Override
    protected void genModelAndMapper(String tableName, String modelName) {
        TableConfiguration tableConfiguration = new TableConfiguration(context);
        tableConfiguration.setTableName(tableName);
        if (StringUtils.isNotEmpty(modelName)){
            tableConfiguration.setDomainObjectName(modelName);
        }
        tableConfiguration.setGeneratedKey(new GeneratedKey("id", "Mysql", true, null));
        context.addTableConfiguration(tableConfiguration);

        List<String> warnings;
        MyBatisGenerator generator;
        try {
            Configuration config = new Configuration();
            config.addContext(context);
            config.validate();

            boolean overwrite = true;
            DefaultShellCallback callback = new DefaultShellCallback(overwrite);
            warnings = new ArrayList<String>();
            generator = new MyBatisGenerator(config, callback, warnings);
            generator.generate(null);
        } catch (Exception e) {
            //TODO 自定义异常
            throw new RuntimeException("生成Model和Mapper失败", e);
        }

        if (generator.getGeneratedJavaFiles().isEmpty() || generator.getGeneratedXmlFiles().isEmpty()) {
            //TODO 自定义异常
            throw new RuntimeException("生成Model和Mapper失败：" + warnings);
        }
        if (StringUtils.isEmpty(modelName)) {
            modelName = tableNameConvertUpperCamel(tableName);
        }
        log.info(LOG_PRE+modelName + ".java 生成成功");
        log.info(LOG_PRE+modelName + "Mapper.java 生成成功");
        log.info(LOG_PRE+modelName + "Mapper.xml 生成成功");
    }


    /**
     * 构建Crud模板的map
     * @param tableName
     * @param modelNameUpperCamel
     * @return
     */
    private Map<String,Object> buildCrudDataMap(String modelNameUpperCamel,String tableName){
        Map<String, Object> data = Maps.newHashMap();

        data.put("date", this.codeDefinitionDetail.getCodeDefinition().getAuthor());
        data.put("author", codeDefinitionDetail.getCodeDefinition().getAuthor());

        data.put("baseRequestMapping", modelNameConvertMappingPath(modelNameUpperCamel));
        data.put("modelNameUpperCamel", modelNameUpperCamel);
        data.put("modelNameLowerCamel", tableNameConvertLowerCamel(tableName));

        data.put("basePackage", codeDefinitionDetail.getBasePackage());
        return data;
    }

    /**
     * 根据配置类模板的data
     * @param codeDefinitionDetail
     * @param dbDefinition
     * @return
     */
    private Map<String, Object> buildConfigDataMap(CodeDefinitionDetail codeDefinitionDetail, DBDefinition dbDefinition) {
        Map<String,Object> data = Maps.newHashMap();
        data.put("driver_class",dbDefinition.getDriverClass());
        data.put("url",dbDefinition.getUrl());
        data.put("username",dbDefinition.getUsername());
        data.put("password",dbDefinition.getPassword());
        data.put("coreMapperPath",codeDefinitionDetail.getMapperInterfaceReference());
        data.put("type_aliases_package",codeDefinitionDetail.getModulePackage());

        data.put("date", codeDefinitionDetail.getCodeDefinition().getAuthor());
        data.put("author", codeDefinitionDetail.getCodeDefinition().getAuthor());
        data.put("basePackage", codeDefinitionDetail.getBasePackage());
        return data;
    }

    /**
     * 获取Mybatis上下文
     * @return
     */
    private Context getContext() {
        DBDefinition dbDefinition = getDbDefinitionFromSession();
        CodeDefinitionDetail codeDefinitionDetail = super.codeDefinitionDetail;

        Context context = new Context(ModelType.FLAT);
        context.setId("Potato");
        context.setTargetRuntime("MyBatis3Simple");
        context.addProperty(PropertyRegistry.CONTEXT_BEGINNING_DELIMITER, "`");
        context.addProperty(PropertyRegistry.CONTEXT_ENDING_DELIMITER, "`");

        JDBCConnectionConfiguration jdbcConnectionConfiguration = new JDBCConnectionConfiguration();
        jdbcConnectionConfiguration.setConnectionURL(dbDefinition.getUrl());
        jdbcConnectionConfiguration.setUserId(dbDefinition.getUsername());
        jdbcConnectionConfiguration.setPassword(dbDefinition.getPassword());
        jdbcConnectionConfiguration.setDriverClass(dbDefinition.getDriverClass());
        context.setJdbcConnectionConfiguration(jdbcConnectionConfiguration);

        PluginConfiguration pluginConfiguration = new PluginConfiguration();
        pluginConfiguration.setConfigurationType("tk.mybatis.mapper.generator.MapperPlugin");
        pluginConfiguration.addProperty("mappers", codeDefinitionDetail.getMapperInterfaceReference());
        context.addPluginConfiguration(pluginConfiguration);

        JavaModelGeneratorConfiguration javaModelGeneratorConfiguration = new JavaModelGeneratorConfiguration();
        javaModelGeneratorConfiguration.setTargetProject(codeDefinitionDetail.getProjectPath() + JAVA_PATH);
        javaModelGeneratorConfiguration.setTargetPackage(codeDefinitionDetail.getModulePackage());
        context.setJavaModelGeneratorConfiguration(javaModelGeneratorConfiguration);

        SqlMapGeneratorConfiguration sqlMapGeneratorConfiguration = new SqlMapGeneratorConfiguration();
        sqlMapGeneratorConfiguration.setTargetProject(codeDefinitionDetail.getProjectPath() + RESOURCES_PATH);
        sqlMapGeneratorConfiguration.setTargetPackage("mapper");
        context.setSqlMapGeneratorConfiguration(sqlMapGeneratorConfiguration);

        JavaClientGeneratorConfiguration javaClientGeneratorConfiguration = new JavaClientGeneratorConfiguration();
        javaClientGeneratorConfiguration.setTargetProject(codeDefinitionDetail.getProjectPath() + JAVA_PATH);
        javaClientGeneratorConfiguration.setTargetPackage(codeDefinitionDetail.getMapperPackage());
        javaClientGeneratorConfiguration.setConfigurationType("XMLMAPPER");
        context.setJavaClientGeneratorConfiguration(javaClientGeneratorConfiguration);
        return context;
    }

    /**
     * 生成代码文件
     * @param data
     * @param file
     * @param ftlName
     * @return
     */
    private void generateFtlCode(Map<String,Object> data,File file,String ftlName){
        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            cfg.getTemplate(ftlName).process(data,
                    new FileWriter(file));
        } catch (Exception e) {
            throw new RuntimeException(LOG_PRE+e.getMessage());
        }
    }

    /**
     * 创建model名称
     * @param tableName
     * @param modelName
     * @return
     */
    private String buildModelNameUpperCamel(String tableName, String modelName){
        return StringUtils.isEmpty(modelName) ? tableNameConvertUpperCamel(tableName) : modelName;
    }

    /**
     * 获取session
     * @return
     */
    private DBDefinition getDbDefinitionFromSession(){
        DBDefinition dbDefinition = null;
        try {
            dbDefinition = (DBDefinition) RequestHolder.getCurrentRequest().getSession().getAttribute("dbDefinition");
        } catch (Exception e) {
            //TODO 自定义异常
            throw new RuntimeException(LOG_PRE+"从session中获取DBDefinition失败",e);
        }
        return dbDefinition;
    }


    /**
     * 测试
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        DefaultCodeGenerator defaultCodeGenerator = new DefaultCodeGenerator();
        CodeDefinition codeDefinition = new CodeDefinition();
        codeDefinition.setArtifactId("demo");
        codeDefinition.setGroupId("com.example");

        codeDefinition.setProjectName("test-demo");
        codeDefinition.setDescription("测试");
        codeDefinition.setVersion("vt2.1");
        CodeDefinitionDetail codeDefinitionDetail = new CodeDefinitionDetail(codeDefinition,"/Users/gaowenfeng/project/data/test-demo");

        defaultCodeGenerator.setCodeDefinitionDetail(codeDefinitionDetail);

        freemarker.template.Configuration cfg = new freemarker.template.Configuration(freemarker.template.Configuration.VERSION_2_3_23);
        cfg.setDirectoryForTemplateLoading(new File(PROJECT_PATH+TEMPLATE_FILE_PATH));
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.IGNORE_HANDLER);

        defaultCodeGenerator.setCfg(cfg);
        defaultCodeGenerator.genCodeByCustomModelName("blog",null);
    }

}
