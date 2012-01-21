package games.strategy.engine.data.annotations;

import games.strategy.engine.data.IAttachment;
import games.strategy.util.IntegerMap;

import java.io.File;
import java.io.FileFilter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

/**
 * A test that validates that all attachment classes have properties with valid setters and getters
 * 
 * @author Klaus Groenbaek
 */
public class ValidateAttachmentsTest extends TestCase
{
	// -----------------------------------------------------------------------
	// instance methods
	// -----------------------------------------------------------------------
	
	/**
	 * Test that the Example Attachment is valid
	 */
	public void testExample()
	{
		final String errors = validateAttachment(ExampleAttachment.class);
		assertTrue(errors.length() == 0);
	}
	
	/**
	 * Tests that the algorithm finds invalidly named field
	 */
	public void testInvalidField()
	{
		final String errors = validateAttachment(InvalidFieldNameExample.class);
		assertTrue(errors.length() > 0);
		assertTrue(errors.indexOf("missing field for setter") != -1);
	}
	
	/**
	 * tests that the algorithm will find invalid annotation on a getters
	 */
	public void testAnnotationOnGetter()
	{
		final String errors = validateAttachment(InvalidGetterExample.class);
		assertTrue(errors.length() > 0);
		assertTrue(errors.indexOf("with @GameProperty which is not a setter") != -1);
	}
	
	/**
	 * Tests that the algorithm will find invalid return types
	 */
	public void testInvalidReturnType()
	{
		final String errors = validateAttachment(InvalidReturnTypeExample.class);
		assertTrue(errors.length() > 0);
		assertTrue(errors.indexOf("has incorrect return type") != -1);
	}
	
	/**
	 * Tests that the algorithm will find invalid clear method
	 */
	public void testInvalidClearMethod()
	{
		final String errors = validateAttachment(InvalidClearExample.class);
		assertTrue(errors.length() > 0);
		assertTrue(errors.indexOf("doesn't have a clear method") != -1);
	}
	
	/**
	 * Tests that the algorithm will find adders that doesn't have type IntegerMap
	 */
	public void testInvalidFieldType()
	{
		final String errors = validateAttachment(InvalidFieldTypeExample.class);
		assertTrue(errors.length() > 0);
		assertTrue(errors.indexOf("is not a Collection or Map or IntegerMap") != -1);
	}
	
	/**
	 * When testAllAttachments doesn't work, we can test specific attachments here.
	 */
	public void testSpecificAttachments()
	{
		final StringBuilder sb = new StringBuilder("");
		sb.append(validateAttachment(games.puzzle.slidingtiles.attachments.Tile.class));
		sb.append(validateAttachment(games.strategy.kingstable.attachments.PlayerAttachment.class));
		sb.append(validateAttachment(games.strategy.kingstable.attachments.TerritoryAttachment.class));
		sb.append(validateAttachment(games.strategy.triplea.attatchments.AbstractConditionsAttachment.class));
		sb.append(validateAttachment(games.strategy.triplea.attatchments.AbstractPlayerRulesAttachment.class));
		sb.append(validateAttachment(games.strategy.triplea.attatchments.AbstractRulesAttachment.class));
		sb.append(validateAttachment(games.strategy.triplea.attatchments.AbstractTriggerAttachment.class));
		sb.append(validateAttachment(games.strategy.triplea.attatchments.CanalAttachment.class));
		sb.append(validateAttachment(games.strategy.triplea.attatchments.PlayerAttachment.class));
		sb.append(validateAttachment(games.strategy.triplea.attatchments.PoliticalActionAttachment.class));
		sb.append(validateAttachment(games.strategy.triplea.attatchments.RelationshipTypeAttachment.class));
		sb.append(validateAttachment(games.strategy.triplea.attatchments.RulesAttachment.class));
		sb.append(validateAttachment(games.strategy.triplea.attatchments.TechAttachment.class));
		sb.append(validateAttachment(games.strategy.triplea.attatchments.TerritoryAttachment.class));
		sb.append(validateAttachment(games.strategy.triplea.attatchments.TerritoryEffectAttachment.class));
		sb.append(validateAttachment(games.strategy.triplea.attatchments.TriggerAttachment.class));
		sb.append(validateAttachment(games.strategy.triplea.attatchments.UnitAttachment.class));
		sb.append(validateAttachment(games.strategy.triplea.attatchments.UnitSupportAttachment.class));
		if (sb.toString().length() > 0)
		{
			System.out.println(sb.toString());
			fail(sb.toString());
		}
	}
	
	/**
	 * Scans the compiled /classes folder and finds all classes that implement IAttachment to verify that
	 * all @GameProperty have valid setters and getters
	 */
	public void testAllAttachments()
	{
		// find the classes folder
		final URL url = getClass().getResource("/");
		File file = null;
		try
		{
			file = new File(url.toURI());
		} catch (final URISyntaxException e)
		{
			fail(e.getMessage());
			e.printStackTrace();
		}
		final String errors = findAttachmentsAndValidate(file);
		if (errors.length() > 0)
		{
			System.out.println(errors);
			fail(errors);
		}
	}
	
	// file to find classes or directory
	FileFilter classOrDirectory = new FileFilter()
	{
		public boolean accept(final File file)
		{
			return file.isDirectory() || file.getName().endsWith(".class");
		}
	};
	
	/**
	 * Recursive method to find all classes that implement IAttachment and validate that they use the @GameProperty
	 * annotation correctly
	 * 
	 * @param file
	 *            the file or directory
	 */
	private String findAttachmentsAndValidate(final File file)
	{
		final StringBuilder sb = new StringBuilder("");
		if (file.isDirectory())
		{
			final File[] files = file.listFiles(classOrDirectory);
			for (final File aFile : files)
			{
				sb.append(findAttachmentsAndValidate(aFile));
			}
		}
		else
		{
			final String fileName = file.getAbsolutePath();
			final String classesRoot = File.separatorChar + "classes" + File.separatorChar;
			final int index = fileName.indexOf(classesRoot) + classesRoot.length();
			String className = fileName.substring(index);
			className = className.replace(File.separator, ".");
			className = className.substring(0, className.lastIndexOf(".class"));
			
			if (isSkipClass(className))
			{
				return "";
			}
			
			Class<?> clazz;
			try
			{
				clazz = Class.forName(className);
				if (!clazz.isInterface() && IAttachment.class.isAssignableFrom(clazz))
				{
					@SuppressWarnings("unchecked")
					final Class<? extends IAttachment> attachmentClass = (Class<? extends IAttachment>) clazz;
					sb.append(validateAttachment(attachmentClass));
				}
			} catch (final ClassNotFoundException e)
			{
				sb.append("Warning: Class " + className + " not found. Error Message: " + e.getMessage() + "\n");
			}
			
		}
		return sb.toString();
	}
	
	/**
	 * todo(kg) fix this
	 * ReliefImageBreaker and TileImageBreaker has a static field that opens a save dialog!!!
	 * "InvalidGetterExample", "InvalidFieldNameExample", "InvalidReturnTypeExample" are skipped because they are purposely invalid, and use to test the validation algorithm
	 * 
	 */
	public static final List<String> SKIPCLASSES = Arrays.asList("ReliefImageBreaker", "TileImageBreaker",
				"InvalidGetterExample", "InvalidFieldNameExample", "InvalidReturnTypeExample", "InvalidClearExample", "InvalidFieldTypeExample");
	
	/**
	 * Contains a list of classes which has static initializes, unfortunately you can't reflect this, since loading the class triggers
	 * the initializer
	 * 
	 * @param className
	 *            the class name
	 * @return true if this class has a static initializer
	 */
	private boolean isSkipClass(final String className)
	{
		for (final String staticInitClass : SKIPCLASSES)
		{
			if (className.contains(staticInitClass))
			{
				return true;
			}
		}
		return false;
	}
	
	private String validateAttachment(final Class<? extends IAttachment> clazz)
	{
		final StringBuilder sb = new StringBuilder("");
		for (final Method setter : clazz.getMethods())
		{
			Method getter;
			if (setter.isAnnotationPresent(GameProperty.class))
			{
				final GameProperty annotation = setter.getAnnotation(GameProperty.class);
				String propertyName = null;
				
				// the property name must be derived from the method name
				if (!setter.getName().startsWith("set"))
				{
					sb.append("Class " + clazz.getCanonicalName() + " has " + setter.getName() + " with @GameProperty which is not a setter\n");
				}
				if (!setter.getReturnType().equals(void.class))
				{
					sb.append("Class " + clazz.getCanonicalName() + " has " + setter.getName() + " ant it doesn't return void\n");
				}
				
				propertyName = getPropertyName(setter);
				
				// For debug purposes only
				// sb.append("TESTING: Class " + clazz.getCanonicalName() + ", setter property " + propertyName + "\n");
				
				// validate that there is a field and a getter
				Field field = null;
				try
				{
					field = clazz.getDeclaredField("m_" + propertyName);
					// adders must have a field of type IntegerMap, for regular setters we don't know the type
					if (annotation.adds())
					{
						if (!(Collection.class.isAssignableFrom(field.getType()) || Map.class.isAssignableFrom(field.getType()) || IntegerMap.class.isAssignableFrom(field.getType())))
						{
							sb.append("Class " + clazz.getCanonicalName() + " has a setter " + setter.getName() + " which adds but the field " + field.getName()
										+ " is not a Collection or Map or IntegerMap\n");
						}
					}
				} catch (final NoSuchFieldException e)
				{
					sb.append("Class " + clazz.getCanonicalName() + " is missing field for setter " + setter.getName() + " with @GameProperty\n");
					continue;
				}
				
				final String getterName = "get" + capitalizeFirstLetter(propertyName);
				try
				{
					// getter must return same type as the field
					final Class<?> type = field.getType();
					
					getter = clazz.getMethod(getterName);
					if (!type.equals(getter.getReturnType()))
					{
						sb.append("Class " + clazz.getCanonicalName() + " has a getter for property " + propertyName + " which has incorrect return type\n");
					}
				} catch (final NoSuchMethodException e)
				{
					sb.append("Class " + clazz.getCanonicalName() + " doesn't have a valid getter method for property: " + propertyName + "\n");
					continue;
				}
				
				if (annotation.adds())
				{
					// check that there is a clear method
					final String clearName = "clear" + capitalizeFirstLetter(propertyName);
					Method clearMethod = null;
					try
					{
						clearMethod = clazz.getMethod(clearName);
					} catch (final NoSuchMethodException e)
					{
						sb.append("Class " + clazz.getCanonicalName() + " doesn't have a clear method for 'adder' property " + propertyName + "\n");
						continue;
					}
					if (!clearMethod.getReturnType().equals(void.class))
					{
						sb.append("Class " + clazz.getCanonicalName() + " has a clear method " + clearMethod.getName() + " that doesn't return void\n");
					}
				}
				else
				{
					// check the symmetry of regular setters
					try
					{
						final Constructor<? extends IAttachment> constructor = clazz.getConstructor(IAttachment.attachmentConstructorParameter);
						final IAttachment attachment = constructor.newInstance("testAttachment", null, null);
						Object value = null;
						if (field.getType().equals(Integer.TYPE))
						{
							value = 5;
						}
						else if (field.getType().equals(Boolean.TYPE))
						{
							value = true;
						}
						else if (field.getType().equals(String.class))
						{
							value = "aString";
						}
						else
						{
							// we do not handle complex types for now
							continue;
						}
						
						setter.invoke(attachment, String.valueOf(value));
						final Object getterValue = getter.invoke(attachment);
						if (!value.equals(getterValue))
						{
							sb.append("Class " + clazz.getCanonicalName() + ", value set could not be obtained using " + getterName + "\n");
						}
						field.setAccessible(true);
						if (!getterValue.equals(field.get(attachment)))
						{
							sb.append("Class " + clazz.getCanonicalName() + ", value obtained through " + getterName + " doesn't match field\n");
						}
						
					} catch (final NoSuchMethodException e)
					{
						sb.append("Warning, Class " + clazz.getCanonicalName() + " testing '" + propertyName + "', has no default constructor\n");
					} catch (final IllegalArgumentException e)
					{
						sb.append("Warning, Class " + clazz.getCanonicalName() + " testing '" + propertyName + "', has error: IllegalArgumentException: " + e.getMessage() + "\n");
					} catch (final InstantiationException e)
					{
						sb.append("Warning, Class " + clazz.getCanonicalName() + " testing '" + propertyName + "', has error: InstantiationException: " + e.getMessage() + "\n");
					} catch (final IllegalAccessException e)
					{
						sb.append("Warning, Class " + clazz.getCanonicalName() + " testing '" + propertyName + "', has error: IllegalAccessException: " + e.getMessage() + "\n");
					} catch (final InvocationTargetException e)
					{
						sb.append("Warning, Class " + clazz.getCanonicalName() + " testing '" + propertyName + "', has error: InvocationTargetException: " + e.getMessage() + "\n");
					}
				}
			}
		}
		return sb.toString();
	}
	
	private String getPropertyName(final Method method)
	{
		final String propertyName = method.getName().substring("set".length());
		char first = propertyName.charAt(0);
		first = Character.toLowerCase(first);
		return first + propertyName.substring(1);
	}
	
	private String capitalizeFirstLetter(final String aString)
	{
		char first = aString.charAt(0);
		first = Character.toUpperCase(first);
		return first + aString.substring(1);
	}
	
}