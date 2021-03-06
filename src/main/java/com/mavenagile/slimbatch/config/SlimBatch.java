package com.mavenagile.slimbatch.config;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.mavenagile.slimbatch.JobExecutionListener;
import com.mavenagile.slimbatch.Partition;
import com.mavenagile.slimbatch.Processor;
import com.mavenagile.slimbatch.Reader;
import com.mavenagile.slimbatch.Writer;
import com.mavenagile.slimbatch.confi.elements.CBean;
import com.mavenagile.slimbatch.confi.elements.CProperties;
import com.mavenagile.slimbatch.confi.elements.CProperty;
import com.mavenagile.slimbatch.confi.elements.Include;
import com.mavenagile.slimbatch.confi.elements.Job;
import com.mavenagile.slimbatch.confi.elements.Listener;
import com.mavenagile.slimbatch.confi.elements.Step;
import com.mavenagile.slimbatch.config.error.ConfigurationException;
import com.mavenagile.slimbatch.jobs.JobRunner;

public class SlimBatch
{
	private static final String STRING_CONST_REPLACE = "Replaced placeholder ${";
	private static final String STRING_CONST_BY = "} by [";
	private static final String STRING_CONST_INBEAN = "] in bean : ";
	private static final String STRING_CONST_DEFINEDBEAN = "] defined for bean : ";
	private static final String STRING_CONST_UNRESOLVED = "Unresolved place holder :";
	private static final String STRING_CONST_DEFINED_AS = "] defined as : ";
	private static final String STRING_CONST_REFERENCED_AT = "] referenced at : ";

	private Log LOGGER = LogFactory.getLog(SlimBatch.class);
	private HashMap<String, JobRunner> oJobRunners;
	private Configuration oConfiguration;
	private Properties oProperties;
	protected static SlimBatch oThisInstance = null;

	private SlimBatch() throws Exception
	{
		init();
	}

	protected synchronized static void instantiateInstance() throws Exception
	{
		// avoid initialization object race
		if (SlimBatch.oThisInstance != null)
			return;
		SlimBatch.oThisInstance = new SlimBatch();
	}

	public static SlimBatch getInstance()
	{
		if (SlimBatch.oThisInstance == null)
			try
			{
				SlimBatch.instantiateInstance();
			}
			catch (Exception ex)
			{
				// ex.printStackTrace();
				SlimBatch.oThisInstance = null;
			}
		return (SlimBatch.oThisInstance);
	}

	private void init() throws Exception
	{
		try
		{
			readConfig();
			resolvePlaceHolders();
			validateBatch();
			oConfiguration.setValid(true);
			oJobRunners = new HashMap<String, JobRunner>();
		}
		catch (Exception e)
		{
			if (oConfiguration != null)
			{
				oConfiguration.setValid(false);
				if (LOGGER.isTraceEnabled())
					LOGGER.error(oConfiguration.toXML());
			}
			throw e;
		}
	}

	private void readConfig() throws Exception
	{
		ParseConfig aParseConfig = new ParseConfig();
		oConfiguration = aParseConfig.getConfiguration();
	}

	private void readPlaceHolders() throws ConfigurationException
	{
		if (oConfiguration != null)
		{
			if (oConfiguration.getPlaceHolder() != null)
			{
				oProperties = new Properties();
				for (Include anInclude : oConfiguration.getPlaceHolder())
				{
					try
					{
						oProperties.load(Configuration.class.getResourceAsStream(anInclude.getResource()));
						if (LOGGER.isTraceEnabled())
							LOGGER.trace("Read placeholder " + anInclude.getResource());
					}
					catch (Exception e)
					{
						throw new ConfigurationException(e, "Invalid placeholder resource : " + anInclude.getResource());
					}
				}
			}
		}
	}

	private void resolveBeansPlaceHolders(HashMap<String, CBean> varCBeans) throws ConfigurationException
	{
		if (varCBeans != null)
		{
			String tmpString;
			String tmpPlaceHolder;
			CBean aBean;
			for (Entry<String, CBean> anEntery : varCBeans.entrySet())
			{
				aBean = anEntery.getValue();
				if (aBean.getBeanClass() != null)
				{
					if (aBean.getBeanClass().startsWith("${") && aBean.getBeanClass().endsWith("}"))
					{
						tmpPlaceHolder = aBean.getBeanClass().substring(2, aBean.getBeanClass().length() - 1);
						tmpString = oProperties.getProperty(tmpPlaceHolder);
						if (tmpString != null)
							aBean.setClass(tmpString);
						else
							throw new ConfigurationException(STRING_CONST_UNRESOLVED + " : bean class [" + aBean.getBeanClass() + STRING_CONST_DEFINEDBEAN + aBean.toXML(0));
						if (LOGGER.isTraceEnabled())
							LOGGER.trace(STRING_CONST_REPLACE + tmpPlaceHolder + STRING_CONST_BY + aBean.getBeanClass() + STRING_CONST_INBEAN + aBean.getID());
					}
				}
				if (aBean.getProperties() != null)
				{
					for (CProperty aProperty : aBean.getProperties())
					{
						// aProperty.getType().equals("string") &&
						if (aProperty.getValue().startsWith("${") && aProperty.getValue().endsWith("}"))
						{
							tmpPlaceHolder = aProperty.getValue().substring(2, aProperty.getValue().length() - 1);
							tmpString = oProperties.getProperty(tmpPlaceHolder);
							if (tmpString != null)
								aProperty.setValue(tmpString);
							else
								throw new ConfigurationException(STRING_CONST_UNRESOLVED + " : Property name [" + aProperty.getValue() + STRING_CONST_DEFINEDBEAN + aBean.toXML(0));
							if (LOGGER.isTraceEnabled())
								LOGGER.trace(STRING_CONST_REPLACE + tmpPlaceHolder + STRING_CONST_BY + aProperty.getValue() + STRING_CONST_INBEAN + aProperty.getName());
						}
					}
				}
			}
		}
	}

	private void resolveBeansPlaceHolders(CProperties varCProperties, CProperties varParentProperties) throws ConfigurationException
	{
		if (varCProperties != null)
		{
			String tmpString;
			String tmpPlaceHolder;
			CProperty tmpCProperty;
			for (CProperty aProperty : varCProperties)
			{
				if (aProperty.getValue().startsWith("${") && aProperty.getValue().endsWith("}"))
				{
					tmpPlaceHolder = aProperty.getValue().substring(2, aProperty.getValue().length() - 1);
					tmpString = oProperties.getProperty(tmpPlaceHolder);
					if (tmpString == null && varParentProperties != null)
					{
						tmpCProperty = varParentProperties.getCProperty(tmpPlaceHolder);

						if (tmpCProperty != null)
							tmpString = tmpCProperty.getValue();
					}
					if (tmpString != null)
						aProperty.setValue(tmpString);
					else
						throw new ConfigurationException(STRING_CONST_UNRESOLVED + " : Property name [" + aProperty.getValue() + STRING_CONST_DEFINED_AS + aProperty.toXML(0));
					if (LOGGER.isTraceEnabled())
						LOGGER.trace(STRING_CONST_REPLACE + tmpPlaceHolder + STRING_CONST_BY + aProperty.getValue() + STRING_CONST_INBEAN + aProperty.getName());
				}
			}
		}
	}

	private void resolvePlaceHolders() throws ConfigurationException
	{
		readPlaceHolders();
		resolveBeansPlaceHolders(oConfiguration.getDataSources());
		resolveBeansPlaceHolders(oConfiguration.getBeans());
	}

	private void validateBatch() throws ConfigurationException
	{
		// validate every bean class
		if (oConfiguration.getBeans() != null)
		{
			CBean aBean;
			for (Entry<String, CBean> anEntry : oConfiguration.getBeans().entrySet())
			{
				aBean = anEntry.getValue();
				if (BeanHelper.findClass(aBean.getBeanClass()) == null)
					throw new ConfigurationException("Class Not Found  : Bean [" + aBean.getBeanClass() + STRING_CONST_DEFINED_AS + aBean.toXML(0));
				if (LOGGER.isTraceEnabled())
					LOGGER.trace("Valid class : " + aBean.getID() + " : " + aBean.getBeanClass());
			}
		}
		if (oConfiguration.getDataSources() != null)
		{
			CBean aBean;
			for (Entry<String, CBean> anEntry : oConfiguration.getDataSources().entrySet())
			{
				aBean = anEntry.getValue();
				if (BeanHelper.findClass(aBean.getBeanClass()) == null)
					throw new ConfigurationException("Class Not Found  : DataSources[" + aBean.getBeanClass() + STRING_CONST_DEFINED_AS + aBean.toXML(0));
				if (LOGGER.isTraceEnabled())
					LOGGER.trace("Valid DataSources class : " + aBean.getBeanClass());
			}
		}
		// validate every bean reference has a matching bean
		// validate Listeners and datasources
		if (oConfiguration.getListeners() != null)
		{
			// validate batch Listeners
			for (Listener aListener : oConfiguration.getListeners())
			{
				if (oConfiguration.getBeans().get(aListener.getReference()) == null)
					throw new ConfigurationException("Undefined batch listener : Listener[" + aListener.getReference() + STRING_CONST_DEFINED_AS + aListener.toXML(0));
				// TODO: Validate Listener??
			}
		}
		if (oConfiguration.getJobs() != null)
		{
			// validate job Listeners and datasource
			for (Job aJob : oConfiguration.getJobs())
			{
				CProperties aJobProperties = aJob.getProperties();
				resolveBeansPlaceHolders(aJobProperties, null);

				if (aJob.getListeners() != null)
				{
					for (Listener aListener : aJob.getListeners())
					{
						if (oConfiguration.getBeans().get(aListener.getReference()) == null)
							throw new ConfigurationException("Undefined job listener : Listener[" + aListener.getReference() + STRING_CONST_DEFINED_AS + aListener.toXML(0));
					}
				}
				if (aJob.getSteps() != null)
				{
					for (Step aStep : aJob.getSteps())
					{
						if (aStep.getTransaction() != null)
						{
							if (oConfiguration.getDataSources().get(aStep.getTransaction().getDataSource()) == null)
								throw new ConfigurationException("Undefined DataSource : DataSource[" + aStep.getTransaction().getDataSource() + "] defined at : " + aStep.getTransaction().toXML(0));
							if (LOGGER.isTraceEnabled())
								LOGGER.trace("Valid Transaction DataSources  : " + aStep.getTransaction().getDataSource());
						}

						CBean tmpBean;
						Class<?> tmpClass;
						if (aStep.getChunk() != null)
						{
							tmpBean = oConfiguration.getBeans().get(aStep.getChunk().getReader().getRef());
							if (tmpBean == null)
								throw new ConfigurationException("Referenced Reader class not found  : Reader[" + aStep.getChunk().getReader().getRef() + STRING_CONST_DEFINED_AS + aStep.getChunk().getReader().toXML(0));
							tmpClass = BeanHelper.findClass(tmpBean.getBeanClass());
							if (Reader.class.isAssignableFrom(tmpClass) == false)
								throw new ConfigurationException("Reader bean should implement " + Reader.class + " : Reader class [" + tmpBean.getBeanClass() + STRING_CONST_REFERENCED_AT + aStep.getChunk().getReader().toXML(0));
							if (LOGGER.isTraceEnabled())
								LOGGER.trace("Valid Reader class : " + tmpBean);

							tmpBean = oConfiguration.getBeans().get(aStep.getChunk().getProcessor().getRef());
							if (tmpBean == null)
								throw new ConfigurationException("Referenced Processor class not found  : Processor[" + aStep.getChunk().getProcessor().getRef() + STRING_CONST_DEFINED_AS + aStep.getChunk().getProcessor().toXML(0));
							tmpClass = BeanHelper.findClass(tmpBean.getBeanClass());
							if (Processor.class.isAssignableFrom(tmpClass) == false)
								throw new ConfigurationException("Processor bean should implement " + Processor.class + " : Processor class [" + tmpBean.getBeanClass() + STRING_CONST_REFERENCED_AT + aStep.getChunk().getProcessor().toXML(0));
							if (LOGGER.isTraceEnabled())
								LOGGER.trace("Valid Processor class : " + tmpBean);

							tmpBean = oConfiguration.getBeans().get(aStep.getChunk().getWriter().getRef());
							if (tmpBean == null)
								throw new ConfigurationException("Referenced Writer class not found  : Writer[" + aStep.getChunk().getWriter().getRef() + STRING_CONST_DEFINED_AS + aStep.getChunk().getWriter().toXML(0));
							tmpClass = BeanHelper.findClass(tmpBean.getBeanClass());
							if (Writer.class.isAssignableFrom(tmpClass) == false)
								throw new ConfigurationException("Writer bean should implement " + Writer.class + " : Writer class [" + tmpBean.getBeanClass() + STRING_CONST_REFERENCED_AT + aStep.getChunk().getWriter().toXML(0));
							if (LOGGER.isTraceEnabled())
								LOGGER.trace("Valid Reader class : " + tmpBean);
						}
						if (aStep.getPartition() != null && aStep.getPartition().getMapper() != null)
						{
							tmpBean = oConfiguration.getBeans().get(aStep.getPartition().getMapper().getRef());
							if (tmpBean == null)
								throw new ConfigurationException("Referenced Partition Mapper class not found  : Mapper[" + aStep.getPartition().getMapper().getRef() + STRING_CONST_DEFINED_AS + aStep.getPartition().toXML(0));
							tmpClass = BeanHelper.findClass(tmpBean.getBeanClass());
							if (Partition.class.isAssignableFrom(tmpClass) == false)
								throw new ConfigurationException("Partition Mapper bean should implement " + Partition.class + " : Partition Mapper class [" + tmpBean.getBeanClass() + STRING_CONST_REFERENCED_AT + aStep.getPartition().toXML(0));
							resolveBeansPlaceHolders(aStep.getPartition().getMapper().getProperties(), aJobProperties);

							if (LOGGER.isTraceEnabled())
								LOGGER.trace("Valid Partition Mapper class : " + tmpBean);
						}
					}
				}
			}
		}

	}

	private Job getJob(String varJobID)
	{
		if (oConfiguration.getJobs() != null)
		{
			for (Job aJob : oConfiguration.getJobs())
			{
				if (aJob.getID().equals(varJobID))
					return (aJob);
			}
		}
		return (null);
	}

	public void listJobs()
	{
		if (oConfiguration.getJobs() != null)
		{
			for (Job aJob : oConfiguration.getJobs())
				System.out.println("Job : " + aJob.getID());
		}
	}

	public Configuration getConfiguration()
	{
		return oConfiguration;
	}

	public JobRunner getJobRunner(String varJobID, JobExecutionListener varJobExecutionListener)
	{
		if (varJobID == null)
			return (null);
		JobRunner aJobRunner = oJobRunners.get(varJobID);
		if (aJobRunner != null)
		{
			return (aJobRunner);
		}
		Job aJob = getJob(varJobID);
		if (aJob != null)
		{
			aJobRunner = new JobRunner(this, aJob, varJobExecutionListener);
			aJobRunner.setID(varJobID);
			oJobRunners.put(varJobID, aJobRunner);
			return (aJobRunner);
		}
		return (null);
	}

	public void jobCompleted(String varJobID)
	{
		oJobRunners.remove(varJobID);
	}
}
