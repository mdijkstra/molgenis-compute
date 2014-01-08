package org.molgenis.compute.db;

import org.molgenis.DatabaseConfig;
import org.molgenis.compute.db.executor.PilotManager;
import org.molgenis.compute.db.executor.Scheduler;
import org.molgenis.compute.db.pilot.ScriptBuilder;
import org.molgenis.compute.db.util.ComputeMolgenisSettings;
import org.molgenis.data.DataService;
import org.molgenis.elasticsearch.config.EmbeddedElasticSearchConfig;
import org.molgenis.framework.db.Database;
import org.molgenis.framework.security.Login;
import org.molgenis.framework.server.MolgenisPermissionService;
import org.molgenis.framework.server.MolgenisSettings;
import org.molgenis.framework.ui.MolgenisPlugin;
import org.molgenis.omx.OmxConfig;
import org.molgenis.omx.auth.OmxPermissionService;
import org.molgenis.omx.config.DataExplorerConfig;
import org.molgenis.search.SearchSecurityConfig;
import org.molgenis.security.user.MolgenisUserService;
import org.molgenis.ui.*;
import org.molgenis.util.ApplicationContextProvider;
import org.molgenis.util.AsyncJavaMailSender;
import org.molgenis.util.GsonHttpMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer;
import org.springframework.web.servlet.view.freemarker.FreeMarkerViewResolver;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

@Configuration
@EnableTransactionManagement
@EnableWebMvc
@EnableScheduling
@ComponentScan("org.molgenis")
@Import(
		{ WebAppSecurityConfig.class, DatabaseConfig.class, OmxConfig.class, EmbeddedElasticSearchConfig.class,
				DataExplorerConfig.class, SearchSecurityConfig.class })
public class WebAppConfig extends MolgenisWebAppConfig
{
	@Autowired
	private DataService dataService;
	@Autowired
	private MolgenisUserService molgenisUserService;

	@Autowired
	private MolgenisSettings molgenisSettings;

	@Bean
	public static PropertySourcesPlaceholderConfigurer properties()
	{
		PropertySourcesPlaceholderConfigurer pspc = new PropertySourcesPlaceholderConfigurer();
		Resource[] resources = new FileSystemResource[]
		{ new FileSystemResource(System.getProperty("user.home") + "/molgenis-server.properties") };
		pspc.setLocations(resources);
		pspc.setIgnoreUnresolvablePlaceholders(true);
		pspc.setIgnoreResourceNotFound(true);
		pspc.setNullValue("@null");
		return pspc;
	}

	@Value("${mail.host:smtp.gmail.com}")
	private String mailHost;
	@Value("${mail.port:587}")
	private Integer mailPort;
	@Value("${mail.protocol:smtp}")
	private String mailProtocol;
	@Value("${mail.username}")
	private String mailUsername; // specify in molgenis-server.properties
	@Value("${mail.password}")
	private String mailPassword; // specify in molgenis-server.properties
	@Value("${mail.java.auth:true}")
	private String mailJavaAuth;
	@Value("${mail.java.starttls.enable:true}")
	private String mailJavaStartTlsEnable;
	@Value("${mail.java.quitwait:false}")
	private String mailJavaQuitWait;

	@Bean
	public JavaMailSender mailSender()
	{
		AsyncJavaMailSender mailSender = new AsyncJavaMailSender();
		mailSender.setHost(mailHost);
		mailSender.setPort(mailPort);
		mailSender.setProtocol(mailProtocol);
		mailSender.setUsername(mailUsername); // specify in
												// molgenis-server.properties
		mailSender.setPassword(mailPassword); // specify in
												// molgenis-server.properties
		Properties javaMailProperties = new Properties();
		javaMailProperties.setProperty("mail.smtp.auth", mailJavaAuth);
		javaMailProperties.setProperty("mail.smtp.starttls.enable", mailJavaStartTlsEnable);
		javaMailProperties.setProperty("mail.smtp.quitwait", mailJavaQuitWait);
		mailSender.setJavaMailProperties(javaMailProperties);
		return mailSender;
	}

	@Value("${api.user.name:api}")
	private String apiUserName; // specify in molgenis-server.properties

	@Value("${api.user.password:api}")
	private String apiUserPassword; // specify in molgenis-server.properties

	@Bean
	public ScriptBuilder scriptBuilder()
	{
		return new ScriptBuilder(apiUserName, apiUserPassword);
	}

	@Scheduled(fixedDelay = 5000)
	public void expired()
	{
		pilotManager().checkExperiredPilots();
	}

	@Bean
	public PilotManager pilotManager()
	{
		return new PilotManager();
	}

	@Bean
	public Scheduler scheduler()
	{
		return new Scheduler(taskScheduler());
	}

	@Bean(destroyMethod = "shutdown")
	public TaskScheduler taskScheduler()
	{
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(10);

		return scheduler;
	}

	@Override
	public void configureDefaultServletHandling(DefaultServletHandlerConfigurer configurer)
	{
		configurer.enable("dispatcher");
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry)
	{
		registry.addResourceHandler("/css/**").addResourceLocations("/css/", "classpath:/css/");
		registry.addResourceHandler("/img/**").addResourceLocations("/img/", "classpath:/img/");
		registry.addResourceHandler("/js/**").addResourceLocations("/js/", "classpath:/js/");
		registry.addResourceHandler("/generated-doc/**").addResourceLocations("/generated-doc/");
		registry.addResourceHandler("/html/**").addResourceLocations("/html/", "classpath:/html/");
	}

	@Override
	public void configureMessageConverters(List<HttpMessageConverter<?>> converters)
	{
		converters.add(new GsonHttpMessageConverter());
	}

	@Bean
	public ApplicationListener<?> databasePopulator()
	{
		return new WebAppDatabasePopulator();
	}

	/**
	 * Bean that allows referencing Spring managed beans from Java code which is not managed by Spring
	 * 
	 * @return
	 */
	@Bean
	public ApplicationContextProvider applicationContextProvider()
	{
		return new ApplicationContextProvider();
	}

	@Bean
	public MolgenisSettings molgenisSettings()
	{
		return new ComputeMolgenisSettings();
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry)
	{
		String pluginInterceptPattern = MolgenisPlugin.PLUGIN_URI_PREFIX + "**";
		registry.addInterceptor(molgenisPluginInterceptor()).addPathPatterns(pluginInterceptPattern);
	}

	@Bean
	public MolgenisPluginInterceptor molgenisPluginInterceptor()
	{
		return new MolgenisPluginInterceptor(login, molgenisPermissionService(), molgenisUi());
	}

	/**
	 * Enable spring freemarker viewresolver. All freemarker template names should end with '.ftl'
	 */
	@Bean
	public ViewResolver viewResolver()
	{
		FreeMarkerViewResolver resolver = new FreeMarkerViewResolver();
		resolver.setCache(true);
		resolver.setSuffix(".ftl");

		return resolver;
	}

	/**
	 * Configure freemarker. All freemarker templates should be on the classpath in a package called 'freemarker'
	 */
	@Bean
	public FreeMarkerConfigurer freeMarkerConfigurer()
	{
		FreeMarkerConfigurer result = new FreeMarkerConfigurer();
		result.setPreferFileSystemAccess(false);
		result.setTemplateLoaderPath("classpath:/templates/");

		return result;
	}


	
	@Bean
	public MolgenisUi molgenisUi()
	{
		try
		{
			return new XmlMolgenisUi(new XmlMolgenisUiLoader(), molgenisSettings, molgenisPermissionService());
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	@Bean
	public MolgenisPermissionService molgenisPermissionService()
	{
		return new OmxPermissionService(unauthorizedDatabase, login);
	}

}