/*******************************************************************************
 * Copyright (c) 2003, 2019 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.osgi.internal.framework;

import static java.util.Objects.requireNonNull;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.osgi.framework.util.CaseInsensitiveDictionaryMap;
import org.eclipse.osgi.internal.debug.Debug;
import org.eclipse.osgi.internal.messages.Msg;
import org.eclipse.osgi.internal.serviceregistry.ServiceReferenceImpl;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;

/**
 * RFC 1960-based Filter. Filter objects can be created by calling the
 * constructor with the desired filter string. A Filter object can be called
 * numerous times to determine if the match argument matches the filter string
 * that was used to create the Filter object.
 * <p>
 * The syntax of a filter string is the string representation of LDAP search
 * filters as defined in RFC 1960: <i>A String Representation of LDAP Search
 * Filters</i> (available at http://www.ietf.org/rfc/rfc1960.txt). It should be
 * noted that RFC 2254: <i>A String Representation of LDAP Search Filters</i>
 * (available at http://www.ietf.org/rfc/rfc2254.txt) supersedes RFC 1960 but
 * only adds extensible matching and is not applicable for this API.
 * <p>
 * The string representation of an LDAP search filter is defined by the
 * following grammar. It uses a prefix format.
 * 
 * <pre>
 *   &lt;filter&gt; ::= '(' &lt;filtercomp&gt; ')'
 *   &lt;filtercomp&gt; ::= &lt;and&gt; | &lt;or&gt; | &lt;not&gt; | &lt;item&gt;
 *   &lt;and&gt; ::= '&amp;' &lt;filterlist&gt;
 *   &lt;or&gt; ::= '|' &lt;filterlist&gt;
 *   &lt;not&gt; ::= '!' &lt;filter&gt;
 *   &lt;filterlist&gt; ::= &lt;filter&gt; | &lt;filter&gt; &lt;filterlist&gt;
 *   &lt;item&gt; ::= &lt;simple&gt; | &lt;present&gt; | &lt;substring&gt;
 *   &lt;simple&gt; ::= &lt;attr&gt; &lt;filtertype&gt; &lt;value&gt;
 *   &lt;filtertype&gt; ::= &lt;equal&gt; | &lt;approx&gt; | &lt;greater&gt; | &lt;less&gt;
 *   &lt;equal&gt; ::= '='
 *   &lt;approx&gt; ::= '&tilde;='
 *   &lt;greater&gt; ::= '&gt;='
 *   &lt;less&gt; ::= '&lt;='
 *   &lt;present&gt; ::= &lt;attr&gt; '=*'
 *   &lt;substring&gt; ::= &lt;attr&gt; '=' &lt;initial&gt; &lt;any&gt; &lt;final&gt;
 *   &lt;initial&gt; ::= NULL | &lt;value&gt;
 *   &lt;any&gt; ::= '*' &lt;starval&gt;
 *   &lt;starval&gt; ::= NULL | &lt;value&gt; '*' &lt;starval&gt;
 *   &lt;final&gt; ::= NULL | &lt;value&gt;
 * </pre>
 * 
 * {@code &lt;attr&gt;} is a string representing an attribute, or key, in the
 * properties objects of the registered services. Attribute names are not case
 * sensitive; that is cn and CN both refer to the same attribute.
 * {@code &lt;value&gt;} is a string representing the value, or part of one, of
 * a key in the properties objects of the registered services. If a
 * {@code &lt;value&gt;} must contain one of the characters ' {@code *}' or
 * '{@code (}' or '{@code )}', these characters should be escaped by preceding
 * them with the backslash '{@code \}' character. Note that although both the
 * {@code &lt;substring&gt;} and {@code &lt;present&gt;} productions can produce
 * the {@code 'attr=*'} construct, this construct is used only to denote a
 * presence filter.
 * <p>
 * Examples of LDAP filters are:
 * 
 * <pre>
 *   &quot;(cn=Babs Jensen)&quot;
 *   &quot;(!(cn=Tim Howes))&quot;
 *   &quot;(&amp;(&quot; + Constants.OBJECTCLASS + &quot;=Person)(|(sn=Jensen)(cn=Babs J*)))&quot;
 *   &quot;(o=univ*of*mich*)&quot;
 * </pre>
 * <p>
 * The approximate match ({@code ~=}) is implementation specific but should at
 * least ignore case and white space differences. Optional are codes like
 * soundex or other smart "closeness" comparisons.
 * <p>
 * Comparison of values is not straightforward. Strings are compared differently
 * than numbers and it is possible for a key to have multiple values. Note that
 * that keys in the match argument must always be strings. The comparison is
 * defined by the object type of the key's value. The following rules apply for
 * comparison: <blockquote>
 * <TABLE BORDER=0>
 * <TR>
 * <TD><b>Property Value Type </b></TD>
 * <TD><b>Comparison Type</b></TD>
 * </TR>
 * <TR>
 * <TD>String</TD>
 * <TD>String comparison</TD>
 * </TR>
 * <TR valign=top>
 * <TD>Integer, Long, Float, Double, Byte, Short, BigInteger, BigDecimal</TD>
 * <TD>numerical comparison</TD>
 * </TR>
 * <TR>
 * <TD>Character</TD>
 * <TD>character comparison</TD>
 * </TR>
 * <TR>
 * <TD>Boolean</TD>
 * <TD>equality comparisons only</TD>
 * </TR>
 * <TR>
 * <TD>[] (array)</TD>
 * <TD>recursively applied to values</TD>
 * </TR>
 * <TR>
 * <TD>Collection</TD>
 * <TD>recursively applied to values</TD>
 * </TR>
 * </TABLE>
 * Note: arrays of primitives are also supported. </blockquote> A filter matches
 * a key that has multiple values if it matches at least one of those values.
 * For example,
 * 
 * <pre>
 * Dictionary d = new Hashtable();
 * d.put(&quot;cn&quot;, new String[] {
 * 		&quot;a&quot;, &quot;b&quot;, &quot;c&quot;
 * });
 * </pre>
 * 
 * d will match {@code (cn=a)} and also {@code (cn=b)}
 * <p>
 * A filter component that references a key having an unrecognizable data type
 * will evaluate to {@code false} .
 */
public abstract class FilterImpl implements Filter {
	/* normalized filter string for Filter object */
	private transient String filterString;

	/**
	 * Creates a {@link FilterImpl} object. This filter object may be used to
	 * match a {@link ServiceReference} or a Dictionary.
	 * <p>
	 * If the filter cannot be parsed, an {@link InvalidSyntaxException} will be
	 * thrown with a human readable message where the filter became unparsable.
	 * 
	 * @param filterString the filter string.
	 * @throws InvalidSyntaxException If the filter parameter contains an
	 *             invalid filter string that cannot be parsed.
	 */
	public static FilterImpl newInstance(String filterString) throws InvalidSyntaxException {
		return newInstance(filterString, false);
	}

	public static FilterImpl newInstance(String filterString, boolean debug) throws InvalidSyntaxException {
		return new Parser(filterString, debug).parse();
	}

	FilterImpl() {
		// empty constructor for subclasses
	}

	/**
	 * Filter using a service's properties.
	 * <p>
	 * This {@code Filter} is executed using the keys and values of the
	 * referenced service's properties. The keys are looked up in a case
	 * insensitive manner.
	 * 
	 * @param reference The reference to the service whose properties are used
	 *            in the match.
	 * @return {@code true} if the service's properties match this
	 *         {@code Filter}; {@code false} otherwise.
	 */
	@Override
	public boolean match(ServiceReference<?> reference) {
		return matches0((reference != null) ? ServiceReferenceMap.asMap(reference) : Collections.<String, Object> emptyMap());
	}

	/**
	 * Filter using a {@code Dictionary} with case insensitive key lookup. This
	 * {@code Filter} is executed using the specified {@code Dictionary}'s keys
	 * and values. The keys are looked up in a case insensitive manner.
	 * 
	 * @param dictionary The {@code Dictionary} whose key/value pairs are used
	 *            in the match.
	 * @return {@code true} if the {@code Dictionary}'s values match this
	 *         filter; {@code false} otherwise.
	 * @throws IllegalArgumentException If {@code dictionary} contains case
	 *             variants of the same key name.
	 */
	@Override
	public boolean match(Dictionary<String, ?> dictionary) {
		return matches0((dictionary != null) ? new CaseInsensitiveDictionaryMap<>(dictionary) : Collections.<String, Object> emptyMap());
	}

	/**
	 * Filter using a {@code Dictionary}. This {@code Filter} is executed using
	 * the specified {@code Dictionary}'s keys and values. The keys are looked
	 * up in a normal manner respecting case.
	 * 
	 * @param dictionary The {@code Dictionary} whose key/value pairs are used
	 *            in the match.
	 * @return {@code true} if the {@code Dictionary}'s values match this
	 *         filter; {@code false} otherwise.
	 * @since 1.3
	 */
	@Override
	public boolean matchCase(Dictionary<String, ?> dictionary) {
		return matches0((dictionary != null) ? DictionaryMap.asMap(dictionary) : Collections.<String, Object> emptyMap());
	}

	/**
	 * Filter using a {@code Map}. This {@code Filter} is executed using the
	 * specified {@code Map}'s keys and values. The keys are looked up in a
	 * normal manner respecting case.
	 * 
	 * @param map The {@code Map} whose key/value pairs are used in the match.
	 *            Maps with {@code null} key or values are not supported. A
	 *            {@code null} value is considered not present to the filter.
	 * @return {@code true} if the {@code Map}'s values match this filter;
	 *         {@code false} otherwise.
	 * @since 1.6
	 */
	@Override
	public boolean matches(Map<String, ?> map) {
		return matches0((map != null) ? map : Collections.<String, Object> emptyMap());
	}

	abstract boolean matches0(Map<String, ?> map);

	/**
	 * Returns this {@code Filter}'s filter string.
	 * <p>
	 * The filter string is normalized by removing whitespace which does not
	 * affect the meaning of the filter.
	 * 
	 * @return This {@code Filter}'s filter string.
	 */
	@Override
	public String toString() {
		String result = filterString;
		if (result == null) {
			filterString = result = normalize(new StringBuilder()).toString();
		}
		return result;
	}

	/**
	 * Returns this {@code Filter}'s normalized filter string.
	 * <p>
	 * The filter string is normalized by removing whitespace which does not
	 * affect the meaning of the filter.
	 * 
	 * @return This {@code Filter}'s filter string.
	 */
	abstract StringBuilder normalize(StringBuilder sb);

	/**
	 * Compares this {@code Filter} to another {@code Filter}.
	 * <p>
	 * This implementation returns the result of calling
	 * {@code this.toString().equals(obj.toString()}.
	 * 
	 * @param obj The object to compare against this {@code Filter}.
	 * @return If the other object is a {@code Filter} object, then returns the
	 *         result of calling {@code this.toString().equals(obj.toString()};
	 *         {@code false} otherwise.
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}

		if (!(obj instanceof Filter)) {
			return false;
		}

		return this.toString().equals(obj.toString());
	}

	/**
	 * Returns the hashCode for this {@code Filter}.
	 * <p>
	 * This implementation returns the result of calling
	 * {@code this.toString().hashCode()}.
	 * 
	 * @return The hashCode of this {@code Filter}.
	 */
	@Override
	public int hashCode() {
		return this.toString().hashCode();
	}

	static final class And extends FilterImpl {
		private final FilterImpl[] operands;

		And(FilterImpl[] operands) {
			this.operands = operands;
		}

		@Override
		boolean matches0(Map<String, ?> map) {
			for (FilterImpl operand : operands) {
				if (!operand.matches0(map)) {
					return false;
				}
			}
			return true;
		}

		@Override
		StringBuilder normalize(StringBuilder sb) {
			sb.append('(').append('&');
			for (FilterImpl operand : operands) {
				operand.normalize(sb);
			}
			return sb.append(')');
		}

		@Override
		public String getPrimaryKeyValue(String primaryKey) {
			// just checking for simple filters here where primaryKey is the only attr or it is one attr of a base '&' clause
			// (primaryKey=org.acme.BrickService) OK
			// (&(primaryKey=org.acme.BrickService)(|(vendor=IBM)(vendor=SUN))) OK
			// (primaryKey=org.acme.*) NOT OK
			// (|(primaryKey=org.acme.BrickService)(primaryKey=org.acme.CementService)) NOT OK
			// (&(primaryKey=org.acme.BrickService)(primaryKey=org.acme.CementService)) OK but only the first primaryKey is returned
			for (FilterImpl operand : operands) {
				if (operand instanceof Equal) {
					String result = operand.getPrimaryKeyValue(primaryKey);
					if (result != null) {
						return result;
					}
				}
			}
			return null;
		}

		@Override
		public List<FilterImpl> getChildren() {
			return new ArrayList<>(Arrays.asList(operands));
		}

		@Override
		void getAttributesInternal(List<String> results) {
			for (FilterImpl operand : operands) {
				operand.getAttributesInternal(results);
			}
		}

		@Override
		void addAttributes(Map<String, String> attributes, Map<String, Range> versionAttrs, boolean not) {
			for (FilterImpl operand : operands) {
				operand.addAttributes(attributes, versionAttrs, false);
			}
		}
	}

	static final class Or extends FilterImpl {
		private final FilterImpl[] operands;

		Or(FilterImpl[] operands) {
			this.operands = operands;
		}

		@Override
		boolean matches0(Map<String, ?> map) {
			for (FilterImpl operand : operands) {
				if (operand.matches0(map)) {
					return true;
				}
			}
			return false;
		}

		@Override
		StringBuilder normalize(StringBuilder sb) {
			sb.append('(').append('|');
			for (FilterImpl operand : operands) {
				operand.normalize(sb);
			}
			return sb.append(')');
		}

		@Override
		public List<FilterImpl> getChildren() {
			return new ArrayList<>(Arrays.asList(operands));
		}

		@Override
		void getAttributesInternal(List<String> results) {
			for (FilterImpl operand : operands) {
				operand.getAttributesInternal(results);
			}
		}

		@Override
		public Map<String, String> getStandardOSGiAttributes(String... versions) {
			throw new IllegalArgumentException("Invalid filter for standard OSGi Attributes: OR"); //$NON-NLS-1$
		}

		@Override
		void addAttributes(Map<String, String> attributes, Map<String, Range> versionAttrs, boolean not) {
			throw new IllegalStateException("Invalid filter for standard OSGi requirements: OR"); //$NON-NLS-1$
		}
	}

	static final class Not extends FilterImpl {
		private final FilterImpl operand;

		Not(FilterImpl operand) {
			this.operand = operand;
		}

		@Override
		boolean matches0(Map<String, ?> map) {
			return !operand.matches0(map);
		}

		@Override
		StringBuilder normalize(StringBuilder sb) {
			sb.append('(').append('!');
			operand.normalize(sb);
			return sb.append(')');
		}

		@Override
		void getAttributesInternal(List<String> results) {
			operand.getAttributesInternal(results);
		}

		@Override
		public Map<String, String> getStandardOSGiAttributes(String... versions) {
			throw new IllegalArgumentException("Invalid filter for standard OSGi Attributes: NOT"); //$NON-NLS-1$
		}

		@Override
		void addAttributes(Map<String, String> attributes, Map<String, Range> versionAttrs, boolean not) {
			operand.addAttributes(attributes, versionAttrs, true);
		}
	}

	static final class Present extends FilterImpl {
		/** debug mode */
		final boolean debug;
		private final String attr;

		Present(String attr, boolean debug) {
			this.attr = attr;
			this.debug = debug;
		}

		@Override
		boolean matches0(Map<String, ?> map) {
			if (debug) {
				Debug.println("PRESENT(" + attr + ")"); //$NON-NLS-1$ //$NON-NLS-2$
			}
			return map.get(attr) != null;
		}

		@Override
		StringBuilder normalize(StringBuilder sb) {
			return sb.append('(').append(attr).append('=').append('*').append(')');
		}

		@Override
		void getAttributesInternal(List<String> results) {
			results.add(attr);
		}

		@Override
		void addAttributes(Map<String, String> attributes, Map<String, Range> versionAttrs, boolean not) {
			attributes.put(attr, "*"); //$NON-NLS-1$
		}
	}

	static abstract class Item extends FilterImpl {
		/** debug mode */
		final boolean debug;
		final String attr;
		final Object value;

		Item(String attr, Object value, boolean debug) {
			this.attr = attr;
			this.value = value;
			this.debug = debug;
		}

		@Override
		boolean matches0(Map<String, ?> map) {
			return compare(map.get(attr), value);
		}

		abstract String operation();

		private boolean compare(Object value1, Object value2) {
			if (debug) {
				if (value1 == null) {
					Debug.println("compare(" + value1 + "," + value2 + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				} else if (!(value1.getClass().isArray() || (value1 instanceof Collection<?>))) {
					Debug.println(operation() + "(" + value1 + "," + value2 + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				}
			}
			if (value1 == null) {
				return false;
			}
			if (value1 instanceof String) {
				return compare_String((String) value1, value2);
			}
			if (value1 instanceof Version) {
				return compare_Version((Version) value1, value2);
			}

			Class<?> clazz = value1.getClass();
			if (clazz.isArray()) {
				Class<?> type = clazz.getComponentType();
				if (type.isPrimitive()) {
					return compare_PrimitiveArray(type, value1, value2);
				}
				return compare_ObjectArray((Object[]) value1, value2);
			}
			if (value1 instanceof Collection<?>) {
				return compare_Collection((Collection<?>) value1, value2);
			}
			if (value1 instanceof Integer) {
				return compare_Integer(((Integer) value1).intValue(), value2);
			}
			if (value1 instanceof Long) {
				return compare_Long(((Long) value1).longValue(), value2);
			}
			if (value1 instanceof Byte) {
				return compare_Byte(((Byte) value1).byteValue(), value2);
			}
			if (value1 instanceof Short) {
				return compare_Short(((Short) value1).shortValue(), value2);
			}
			if (value1 instanceof Character) {
				return compare_Character(((Character) value1).charValue(), value2);
			}
			if (value1 instanceof Float) {
				return compare_Float(((Float) value1).floatValue(), value2);
			}
			if (value1 instanceof Double) {
				return compare_Double(((Double) value1).doubleValue(), value2);
			}
			if (value1 instanceof Boolean) {
				return compare_Boolean(((Boolean) value1).booleanValue(), value2);
			}
			if (value1 instanceof Comparable<?>) {
				@SuppressWarnings("unchecked")
				Comparable<Object> comparable = (Comparable<Object>) value1;
				return compare_Comparable(comparable, value2);
			}
			return compare_Unknown(value1, value2);
		}

		private boolean compare_Collection(Collection<?> collection, Object value2) {
			for (Object value1 : collection) {
				if (compare(value1, value2)) {
					return true;
				}
			}
			return false;
		}

		private boolean compare_ObjectArray(Object[] array, Object value2) {
			for (Object value1 : array) {
				if (compare(value1, value2)) {
					return true;
				}
			}
			return false;
		}

		private boolean compare_PrimitiveArray(Class<?> type, Object primarray, Object value2) {
			if (Integer.TYPE.isAssignableFrom(type)) {
				int[] array = (int[]) primarray;
				for (int value1 : array) {
					if (debug) {
						Debug.println(operation() + "(" + value1 + "," + value2 + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					}
					if (compare_Integer(value1, value2)) {
						return true;
					}
				}
				return false;
			}
			if (Long.TYPE.isAssignableFrom(type)) {
				long[] array = (long[]) primarray;
				for (long value1 : array) {
					if (debug) {
						Debug.println(operation() + "(" + value1 + "," + value2 + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					}
					if (compare_Long(value1, value2)) {
						return true;
					}
				}
				return false;
			}
			if (Byte.TYPE.isAssignableFrom(type)) {
				byte[] array = (byte[]) primarray;
				for (byte value1 : array) {
					if (debug) {
						Debug.println(operation() + "(" + value1 + "," + value2 + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					}
					if (compare_Byte(value1, value2)) {
						return true;
					}
				}
				return false;
			}
			if (Short.TYPE.isAssignableFrom(type)) {
				short[] array = (short[]) primarray;
				for (short value1 : array) {
					if (debug) {
						Debug.println(operation() + "(" + value1 + "," + value2 + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					}
					if (compare_Short(value1, value2)) {
						return true;
					}
				}
				return false;
			}
			if (Character.TYPE.isAssignableFrom(type)) {
				char[] array = (char[]) primarray;
				for (char value1 : array) {
					if (debug) {
						Debug.println(operation() + "(" + value1 + "," + value2 + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					}
					if (compare_Character(value1, value2)) {
						return true;
					}
				}
				return false;
			}
			if (Float.TYPE.isAssignableFrom(type)) {
				float[] array = (float[]) primarray;
				for (float value1 : array) {
					if (debug) {
						Debug.println(operation() + "(" + value1 + "," + value2 + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					}
					if (compare_Float(value1, value2)) {
						return true;
					}
				}
				return false;
			}
			if (Double.TYPE.isAssignableFrom(type)) {
				double[] array = (double[]) primarray;
				for (double value1 : array) {
					if (debug) {
						Debug.println(operation() + "(" + value1 + "," + value2 + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					}
					if (compare_Double(value1, value2)) {
						return true;
					}
				}
				return false;
			}
			if (Boolean.TYPE.isAssignableFrom(type)) {
				boolean[] array = (boolean[]) primarray;
				for (boolean value1 : array) {
					if (debug) {
						Debug.println(operation() + "(" + value1 + "," + value2 + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					}
					if (compare_Boolean(value1, value2)) {
						return true;
					}
				}
				return false;
			}
			return false;
		}

		boolean compare_String(String string, Object value2) {
			return false;
		}

		boolean compare_Version(Version value1, Object value2) {
			return false;
		}

		boolean compare_Comparable(Comparable<Object> value1, Object value2) {
			return false;
		}

		boolean compare_Unknown(Object value1, Object value2) {
			return false;
		}

		boolean compare_Boolean(boolean boolval, Object value2) {
			return false;
		}

		boolean compare_Byte(byte byteval, Object value2) {
			return false;
		}

		boolean compare_Character(char charval, Object value2) {
			return false;
		}

		boolean compare_Double(double doubleval, Object value2) {
			return false;
		}

		boolean compare_Float(float floatval, Object value2) {
			return false;
		}

		boolean compare_Integer(int intval, Object value2) {
			return false;
		}

		boolean compare_Long(long longval, Object value2) {
			return false;
		}

		boolean compare_Short(short shortval, Object value2) {
			return false;
		}

		/**
		 * Encode the value string such that '(', '*', ')' and '\' are escaped.
		 * 
		 * @param value unencoded value string.
		 */
		static StringBuilder encodeValue(StringBuilder sb, String value) {
			for (int i = 0, len = value.length(); i < len; i++) {
				char c = value.charAt(i);
				switch (c) {
					case '(' :
					case '*' :
					case ')' :
					case '\\' :
						sb.append('\\');
						// FALL-THROUGH
					default :
						sb.append(c);
						break;
				}
			}
			return sb;
		}

		@Override
		void getAttributesInternal(List<String> results) {
			results.add(attr);
		}

		static Object valueOf(Class<?> target, String value2) {
			do {
				Method method;
				try {
					method = target.getMethod("valueOf", String.class); //$NON-NLS-1$
				} catch (NoSuchMethodException e) {
					break;
				}
				if (Modifier.isStatic(method.getModifiers()) && target.isAssignableFrom(method.getReturnType())) {
					setAccessible(method);
					try {
						return method.invoke(null, value2.trim());
					} catch (Error e) {
						throw e;
					} catch (Throwable e) {
						return null;
					}
				}
			} while (false);

			do {
				Constructor<?> constructor;
				try {
					constructor = target.getConstructor(String.class);
				} catch (NoSuchMethodException e) {
					break;
				}
				setAccessible(constructor);
				try {
					return constructor.newInstance(value2.trim());
				} catch (Error e) {
					throw e;
				} catch (Throwable e) {
					return null;
				}
			} while (false);

			return null;
		}

		private static void setAccessible(final AccessibleObject accessible) {
			if (!accessible.isAccessible()) {
				AccessController.doPrivileged(new PrivilegedAction<Void>() {
					@Override
					public Void run() {
						accessible.setAccessible(true);
						return null;
					}
				});
			}
		}
	}

	static final class Substring extends Item {
		Substring(String attr, Object value, boolean debug) {
			super(attr, value, debug);
		}

		@Override
		String operation() {
			return "SUBSTRING"; //$NON-NLS-1$
		}

		@Override
		boolean compare_String(String string, Object value2) {
			String[] substrings = (String[]) value2;
			int pos = 0;
			for (int i = 0, size = substrings.length; i < size; i++) {
				String substr = substrings[i];
				if (i + 1 < size) /* if this is not that last substr */ {
					if (substr == null) /* * */ {
						String substr2 = substrings[i + 1];
						if (substr2 == null) /* ** */
							continue; /* ignore first star */
						/* xxx */
						if (debug) {
							Debug.println("indexOf(\"" + substr2 + "\"," + pos + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						}
						int index = string.indexOf(substr2, pos);
						if (index == -1) {
							return false;
						}
						pos = index + substr2.length();
						if (i + 2 < size) // if there are more
							// substrings, increment
							// over the string we just
							// matched; otherwise need
							// to do the last substr
							// check
							i++;
					} else /* xxx */ {
						int len = substr.length();
						if (debug) {
							Debug.println("regionMatches(" + pos + ",\"" + substr + "\")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						}
						if (string.regionMatches(pos, substr, 0, len)) {
							pos += len;
						} else {
							return false;
						}
					}
				} else /* last substr */ {
					if (substr == null) /* * */ {
						return true;
					}
					/* xxx */
					if (debug) {
						Debug.println("regionMatches(" + pos + "," + substr + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					}
					return string.endsWith(substr);
				}
			}
			return true;
		}

		@Override
		StringBuilder normalize(StringBuilder sb) {
			sb.append('(').append(attr).append('=');
			return value(sb).append(')');
		}

		private StringBuilder value(StringBuilder sb) {
			String[] substrings = (String[]) value;
			for (String substr : substrings) {
				if (substr == null) /* * */ {
					sb.append('*');
				} else /* xxx */ {
					encodeValue(sb, substr);
				}
			}
			return sb;
		}

		@Override
		void addAttributes(Map<String, String> attributes, Map<String, Range> versionAttrs, boolean not) {
			attributes.put(attr, value(new StringBuilder()).toString());
		}
	}

	static class Equal extends Item {
		Equal(String attr, Object value, boolean debug) {
			super(attr, value, debug);
		}

		@Override
		String operation() {
			return "EQUAL"; //$NON-NLS-1$
		}

		@Override
		boolean compare_String(String string, Object value2) {
			return string.equals(value2);
		}

		@Override
		boolean compare_Version(Version value1, Object value2) {
			try {
				Version version2 = Version.valueOf((String) value2);
				return value1.compareTo(version2) == 0;
			} catch (Exception e) {
				// if the valueOf or compareTo method throws an exception
				return false;
			}
		}

		@Override
		boolean compare_Boolean(boolean boolval, Object value2) {
			return boolval == Boolean.valueOf(((String) value2).trim()).booleanValue();
		}

		@Override
		boolean compare_Byte(byte byteval, Object value2) {
			try {
				return byteval == Byte.parseByte(((String) value2).trim());
			} catch (IllegalArgumentException e) {
				return false;
			}
		}

		@Override
		boolean compare_Character(char charval, Object value2) {
			try {
				return charval == ((String) value2).charAt(0);
			} catch (IndexOutOfBoundsException e) {
				return false;
			}
		}

		@Override
		boolean compare_Double(double doubleval, Object value2) {
			double doubleval2;
			try {
				doubleval2 = Double.parseDouble(((String) value2).trim());
			} catch (IllegalArgumentException e) {
				return false;
			}
			return Double.compare(doubleval, doubleval2) == 0;
		}

		@Override
		boolean compare_Float(float floatval, Object value2) {
			float floatval2;
			try {
				floatval2 = Float.parseFloat(((String) value2).trim());
			} catch (IllegalArgumentException e) {
				return false;
			}
			return Float.compare(floatval, floatval2) == 0;
		}

		@Override
		boolean compare_Integer(int intval, Object value2) {
			try {
				return intval == Integer.parseInt(((String) value2).trim());
			} catch (IllegalArgumentException e) {
				return false;
			}
		}

		@Override
		boolean compare_Long(long longval, Object value2) {
			try {
				return longval == Long.parseLong(((String) value2).trim());
			} catch (IllegalArgumentException e) {
				return false;
			}
		}

		@Override
		boolean compare_Short(short shortval, Object value2) {
			try {
				return shortval == Short.parseShort(((String) value2).trim());
			} catch (IllegalArgumentException e) {
				return false;
			}
		}

		@Override
		boolean compare_Comparable(Comparable<Object> value1, Object value2) {
			value2 = valueOf(value1.getClass(), (String) value2);
			if (value2 == null) {
				return false;
			}
			try {
				return value1.compareTo(value2) == 0;
			} catch (Exception e) {
				// if the compareTo method throws an exception; return false
				return false;
			}
		}

		@Override
		boolean compare_Unknown(Object value1, Object value2) {
			value2 = valueOf(value1.getClass(), (String) value2);
			if (value2 == null) {
				return false;
			}
			try {
				return value1.equals(value2);
			} catch (Exception e) {
				// if the equals method throws an exception; return false
				return false;
			}
		}

		@Override
		StringBuilder normalize(StringBuilder sb) {
			sb.append('(').append(attr).append('=');
			return encodeValue(sb, (String) value).append(')');
		}

		@Override
		public String getPrimaryKeyValue(String primaryKey) {
			if (attr.equalsIgnoreCase(primaryKey) && (value instanceof String)) {
				return (String) value;
			}
			return null;
		}

		@Override
		void addAttributes(Map<String, String> attributes, Map<String, Range> versionAttrs, boolean not) {
			if (!versionAttrs.containsKey(attr)) {
				attributes.put(attr, (String) value);
			} else {
				// this is an exact range e.g. [value,value]
				Range currentRange = versionAttrs.get(attr);
				if (currentRange != null) {
					if (not) {
						// this is an expanded form of the filter, e.g.:
						// [1.0,2.0) -> (&(version>=1.0)(version<=2.0)(!(version=2.0)))
						currentRange.addExclude(Version.valueOf((String) value));
					} else {
						throw new IllegalStateException("Invalid range for: " + attr); //$NON-NLS-1$
					}
				} else {
					currentRange = new Range();
					Version version = Version.valueOf((String) value);
					currentRange.setLeft('[', version);
					currentRange.setRight(']', version);
					versionAttrs.put(attr, currentRange);
				}
			}
		}
	}

	static final class Less extends Equal {
		Less(String attr, Object value, boolean debug) {
			super(attr, value, debug);
		}

		@Override
		String operation() {
			return "LESS"; //$NON-NLS-1$
		}

		@Override
		boolean compare_String(String string, Object value2) {
			return string.compareTo((String) value2) <= 0;
		}

		@Override
		boolean compare_Version(Version value1, Object value2) {
			try {
				Version version2 = Version.valueOf((String) value2);
				return value1.compareTo(version2) <= 0;
			} catch (Exception e) {
				// if the valueOf or compareTo method throws an exception
				return false;
			}
		}

		@Override
		boolean compare_Byte(byte byteval, Object value2) {
			try {
				return byteval <= Byte.parseByte(((String) value2).trim());
			} catch (IllegalArgumentException e) {
				return false;
			}
		}

		@Override
		boolean compare_Character(char charval, Object value2) {
			try {
				return charval <= ((String) value2).charAt(0);
			} catch (IndexOutOfBoundsException e) {
				return false;
			}
		}

		@Override
		boolean compare_Double(double doubleval, Object value2) {
			double doubleval2;
			try {
				doubleval2 = Double.parseDouble(((String) value2).trim());
			} catch (IllegalArgumentException e) {
				return false;
			}
			return Double.compare(doubleval, doubleval2) <= 0;
		}

		@Override
		boolean compare_Float(float floatval, Object value2) {
			float floatval2;
			try {
				floatval2 = Float.parseFloat(((String) value2).trim());
			} catch (IllegalArgumentException e) {
				return false;
			}
			return Float.compare(floatval, floatval2) <= 0;
		}

		@Override
		boolean compare_Integer(int intval, Object value2) {
			try {
				return intval <= Integer.parseInt(((String) value2).trim());
			} catch (IllegalArgumentException e) {
				return false;
			}
		}

		@Override
		boolean compare_Long(long longval, Object value2) {
			try {
				return longval <= Long.parseLong(((String) value2).trim());
			} catch (IllegalArgumentException e) {
				return false;
			}
		}

		@Override
		boolean compare_Short(short shortval, Object value2) {
			try {
				return shortval <= Short.parseShort(((String) value2).trim());
			} catch (IllegalArgumentException e) {
				return false;
			}
		}

		@Override
		boolean compare_Comparable(Comparable<Object> value1, Object value2) {
			value2 = valueOf(value1.getClass(), (String) value2);
			if (value2 == null) {
				return false;
			}
			try {
				return value1.compareTo(value2) <= 0;
			} catch (Exception e) {
				// if the compareTo method throws an exception; return false
				return false;
			}
		}

		@Override
		StringBuilder normalize(StringBuilder sb) {
			sb.append('(').append(attr).append('<').append('=');
			return encodeValue(sb, (String) value).append(')');
		}

		@Override
		public String getPrimaryKeyValue(String primaryKey) {
			return null;
		}

		@Override
		public Map<String, String> getStandardOSGiAttributes(String... versions) {
			throw new IllegalArgumentException("Invalid filter for standard OSGi Attributes: " + operation()); //$NON-NLS-1$
		}

		@Override
		void addAttributes(Map<String, String> attributes, Map<String, Range> versionAttrs, boolean not) {
			if (!versionAttrs.containsKey(attr)) {
				throw new IllegalStateException("Invalid attribute: " + attr); //$NON-NLS-1$
			}
			Range currentRange = versionAttrs.get(attr);
			if (currentRange == null) {
				currentRange = new Range();
				versionAttrs.put(attr, currentRange);
			}
			if (not) {
				// this must be a range start "(value"
				if (!currentRange.setLeft('(', Version.valueOf((String) value))) {
					throw new IllegalStateException("range start is already processed for attribute: " + attr); //$NON-NLS-1$
				}
			} else {
				// this must be a range end "value]"
				if (!currentRange.setRight(']', Version.valueOf((String) value))) {
					throw new IllegalStateException("range end is already processed for attribute: " + attr); //$NON-NLS-1$
				}
			}
		}
	}

	static final class Greater extends Equal {
		Greater(String attr, Object value, boolean debug) {
			super(attr, value, debug);
		}

		@Override
		String operation() {
			return "GREATER"; //$NON-NLS-1$
		}

		@Override
		boolean compare_String(String string, Object value2) {
			return string.compareTo((String) value2) >= 0;
		}

		@Override
		boolean compare_Version(Version value1, Object value2) {
			try {
				Version version2 = Version.valueOf((String) value2);
				return value1.compareTo(version2) >= 0;
			} catch (Exception e) {
				// if the valueOf or compareTo method throws an exception
				return false;
			}
		}

		@Override
		boolean compare_Byte(byte byteval, Object value2) {
			try {
				return byteval >= Byte.parseByte(((String) value2).trim());
			} catch (IllegalArgumentException e) {
				return false;
			}
		}

		@Override
		boolean compare_Character(char charval, Object value2) {
			try {
				return charval >= ((String) value2).charAt(0);
			} catch (IndexOutOfBoundsException e) {
				return false;
			}
		}

		@Override
		boolean compare_Double(double doubleval, Object value2) {
			double doubleval2;
			try {
				doubleval2 = Double.parseDouble(((String) value2).trim());
			} catch (IllegalArgumentException e) {
				return false;
			}
			return Double.compare(doubleval, doubleval2) >= 0;
		}

		@Override
		boolean compare_Float(float floatval, Object value2) {
			float floatval2;
			try {
				floatval2 = Float.parseFloat(((String) value2).trim());
			} catch (IllegalArgumentException e) {
				return false;
			}
			return Float.compare(floatval, floatval2) >= 0;
		}

		@Override
		boolean compare_Integer(int intval, Object value2) {
			try {
				return intval >= Integer.parseInt(((String) value2).trim());
			} catch (IllegalArgumentException e) {
				return false;
			}
		}

		@Override
		boolean compare_Long(long longval, Object value2) {
			try {
				return longval >= Long.parseLong(((String) value2).trim());
			} catch (IllegalArgumentException e) {
				return false;
			}
		}

		@Override
		boolean compare_Short(short shortval, Object value2) {
			try {
				return shortval >= Short.parseShort(((String) value2).trim());
			} catch (IllegalArgumentException e) {
				return false;
			}
		}

		@Override
		boolean compare_Comparable(Comparable<Object> value1, Object value2) {
			value2 = valueOf(value1.getClass(), (String) value2);
			if (value2 == null) {
				return false;
			}
			try {
				return value1.compareTo(value2) >= 0;
			} catch (Exception e) {
				// if the compareTo method throws an exception; return false
				return false;
			}
		}

		@Override
		StringBuilder normalize(StringBuilder sb) {
			sb.append('(').append(attr).append('>').append('=');
			return encodeValue(sb, (String) value).append(')');
		}

		@Override
		public String getPrimaryKeyValue(String primaryKey) {
			return null;
		}

		@Override
		public Map<String, String> getStandardOSGiAttributes(String... versions) {
			throw new IllegalArgumentException("Invalid filter for standard OSGi Attributes: " + operation()); //$NON-NLS-1$
		}

		@Override
		void addAttributes(Map<String, String> attributes, Map<String, Range> versionAttrs, boolean not) {
			if (!versionAttrs.containsKey(attr)) {
				throw new IllegalStateException("Invalid attribute: " + attr); //$NON-NLS-1$
			}
			Range currentRange = versionAttrs.get(attr);
			if (currentRange == null) {
				currentRange = new Range();
				versionAttrs.put(attr, currentRange);
			}
			if (not) {
				// this must be a range end "value)"
				if (!currentRange.setRight(')', Version.valueOf((String) value))) {
					throw new IllegalStateException("range end is already processed for attribute: " + attr); //$NON-NLS-1$
				}
			} else {
				// this must be a range start "[value"
				if (!currentRange.setLeft('[', Version.valueOf((String) value))) {
					throw new IllegalStateException("range start is already processed for attribute: " + attr); //$NON-NLS-1$
				}
			}
		}
	}

	static final class Approx extends Equal {
		Approx(String attr, Object value, boolean debug) {
			super(attr, value, debug);
		}

		@Override
		String operation() {
			return "APPROX"; //$NON-NLS-1$
		}

		@Override
		boolean compare_String(String string, Object value2) {
			string = approxString(string);
			String string2 = approxString((String) value2);
			return string.equalsIgnoreCase(string2);
		}

		@Override
		boolean compare_Character(char charval, Object value2) {
			char charval2;
			try {
				charval2 = ((String) value2).charAt(0);
			} catch (IndexOutOfBoundsException e) {
				return false;
			}
			return (charval == charval2) || (Character.toUpperCase(charval) == Character.toUpperCase(charval2)) || (Character.toLowerCase(charval) == Character.toLowerCase(charval2));
		}

		@Override
		StringBuilder normalize(StringBuilder sb) {
			sb.append('(').append(attr).append('~').append('=');
			return encodeValue(sb, approxString((String) value)).append(')');
		}

		/**
		 * Map a string for an APPROX (~=) comparison. This implementation
		 * removes white spaces. This is the minimum implementation allowed by
		 * the OSGi spec.
		 * 
		 * @param input Input string.
		 * @return String ready for APPROX comparison.
		 */
		static String approxString(String input) {
			boolean changed = false;
			char[] output = input.toCharArray();
			int cursor = 0;
			for (char c : output) {
				if (Character.isWhitespace(c)) {
					changed = true;
					continue;
				}

				output[cursor] = c;
				cursor++;
			}

			return changed ? new String(output, 0, cursor) : input;
		}

		@Override
		public String getPrimaryKeyValue(String primaryKey) {
			return null;
		}

		@Override
		public Map<String, String> getStandardOSGiAttributes(String... versions) {
			throw new IllegalArgumentException("Invalid filter for standard OSGi Attributes: " + operation()); //$NON-NLS-1$
		}
	}

	/**
	 * Returns the leftmost required objectClass value for the filter to evaluate to true.
	 * 
	 * @return The leftmost required objectClass value or null if none could be determined.
	 */
	public String getRequiredObjectClass() {
		return getPrimaryKeyValue(Constants.OBJECTCLASS);
	}

	/**
	 * Returns the leftmost required primary key value for the filter to evaluate to true.
	 * This is useful for indexing candidates to match against this filter.
	 * @param primaryKey the primary key
	 * @return The leftmost required primary key value or null if none could be determined.
	 */
	public String getPrimaryKeyValue(String primaryKey) {
		// just checking for simple filters here where primaryKey is the only attr or it is one attr of a base '&' clause
		// (primaryKey=org.acme.BrickService) OK
		// (&(primaryKey=org.acme.BrickService)(|(vendor=IBM)(vendor=SUN))) OK
		// (primaryKey=org.acme.*) NOT OK
		// (|(primaryKey=org.acme.BrickService)(primaryKey=org.acme.CementService)) NOT OK
		// (&(primaryKey=org.acme.BrickService)(primaryKey=org.acme.CementService)) OK but only the first objectClass is returned
		return null;
	}

	public List<FilterImpl> getChildren() {
		return Collections.emptyList();
	}

	/**
	 * Returns all the attributes contained within this filter
	 * @return all the attributes contained within this filter
	 */
	public String[] getAttributes() {
		List<String> results = new ArrayList<>();
		getAttributesInternal(results);
		return results.toArray(new String[0]);
	}

	abstract void getAttributesInternal(List<String> results);

	public Map<String, String> getStandardOSGiAttributes(String... versions) {
		Map<String, String> result = new HashMap<>();
		Map<String, Range> versionAttrs = new HashMap<>();
		if (versions != null) {
			for (String versionAttr : versions) {
				versionAttrs.put(versionAttr, null);
			}
		}
		addAttributes(result, versionAttrs, false);
		for (Map.Entry<String, Range> entry : versionAttrs.entrySet()) {
			Range range = entry.getValue();
			if (range != null) {
				result.put(entry.getKey(), range.toString());
			}
		}

		return result;
	}

	abstract void addAttributes(Map<String, String> attributes, Map<String, Range> versionAttrs, boolean not);

	/**
	 * Parser class for OSGi filter strings. This class parses the complete
	 * filter string and builds a tree of FilterImpl objects rooted at the
	 * parent.
	 */
	static private final class Parser {
		private final boolean debug;
		private final String filterstring;
		private final char[] filterChars;
		private int pos;

		Parser(String filterstring, boolean debug) {
			this.debug = debug;
			this.filterstring = filterstring;
			filterChars = filterstring.toCharArray();
			pos = 0;
		}

		FilterImpl parse() throws InvalidSyntaxException {
			FilterImpl filter;
			try {
				filter = parse_filter();
			} catch (ArrayIndexOutOfBoundsException e) {
				throw new InvalidSyntaxException(Msg.FILTER_TERMINATED_ABRUBTLY, filterstring, e);
			}

			if (pos != filterChars.length) {
				throw new InvalidSyntaxException(NLS.bind(Msg.FILTER_TRAILING_CHARACTERS, filterstring.substring(pos)), filterstring);
			}
			return filter;
		}

		private FilterImpl parse_filter() throws InvalidSyntaxException {
			FilterImpl filter;
			skipWhiteSpace();

			if (filterChars[pos] != '(') {
				throw new InvalidSyntaxException(NLS.bind(Msg.FILTER_MISSING_LEFTPAREN, filterstring.substring(pos)), filterstring);
			}

			pos++;

			filter = parse_filtercomp();

			skipWhiteSpace();

			if (filterChars[pos] != ')') {
				throw new InvalidSyntaxException(NLS.bind(Msg.FILTER_MISSING_RIGHTPAREN, filterstring.substring(pos)), filterstring);
			}

			pos++;

			skipWhiteSpace();

			return filter;
		}

		private FilterImpl parse_filtercomp() throws InvalidSyntaxException {
			skipWhiteSpace();

			char c = filterChars[pos];

			switch (c) {
				case '&' : {
					pos++;
					return parse_and();
				}
				case '|' : {
					pos++;
					return parse_or();
				}
				case '!' : {
					pos++;
					return parse_not();
				}
			}
			return parse_item();
		}

		private FilterImpl parse_and() throws InvalidSyntaxException {
			int lookahead = pos;
			skipWhiteSpace();

			if (filterChars[pos] != '(') {
				pos = lookahead - 1;
				return parse_item();
			}

			List<FilterImpl> operands = new ArrayList<>(10);

			while (filterChars[pos] == '(') {
				FilterImpl child = parse_filter();
				operands.add(child);
			}

			return new FilterImpl.And(operands.toArray(new FilterImpl[0]));
		}

		private FilterImpl parse_or() throws InvalidSyntaxException {
			int lookahead = pos;
			skipWhiteSpace();

			if (filterChars[pos] != '(') {
				pos = lookahead - 1;
				return parse_item();
			}

			List<FilterImpl> operands = new ArrayList<>(10);

			while (filterChars[pos] == '(') {
				FilterImpl child = parse_filter();
				operands.add(child);
			}

			return new FilterImpl.Or(operands.toArray(new FilterImpl[0]));
		}

		private FilterImpl parse_not() throws InvalidSyntaxException {
			int lookahead = pos;
			skipWhiteSpace();

			if (filterChars[pos] != '(') {
				pos = lookahead - 1;
				return parse_item();
			}

			FilterImpl child = parse_filter();

			return new FilterImpl.Not(child);
		}

		private FilterImpl parse_item() throws InvalidSyntaxException {
			String attr = parse_attr();

			skipWhiteSpace();

			switch (filterChars[pos]) {
				case '~' : {
					if (filterChars[pos + 1] == '=') {
						pos += 2;
						return new FilterImpl.Approx(attr, parse_value(), debug);
					}
					break;
				}
				case '>' : {
					if (filterChars[pos + 1] == '=') {
						pos += 2;
						return new FilterImpl.Greater(attr, parse_value(), debug);
					}
					break;
				}
				case '<' : {
					if (filterChars[pos + 1] == '=') {
						pos += 2;
						return new FilterImpl.Less(attr, parse_value(), debug);
					}
					break;
				}
				case '=' : {
					if (filterChars[pos + 1] == '*') {
						int oldpos = pos;
						pos += 2;
						skipWhiteSpace();
						if (filterChars[pos] == ')') {
							return new FilterImpl.Present(attr, debug);
						}
						pos = oldpos;
					}

					pos++;
					Object string = parse_substring();

					if (string instanceof String) {
						return new FilterImpl.Equal(attr, string, debug);
					}
					return new FilterImpl.Substring(attr, string, debug);
				}
			}

			throw new InvalidSyntaxException(NLS.bind(Msg.FILTER_INVALID_OPERATOR, filterstring.substring(pos)), filterstring);
		}

		private String parse_attr() throws InvalidSyntaxException {
			skipWhiteSpace();

			int begin = pos;
			int end = pos;

			char c = filterChars[pos];

			while (c != '~' && c != '<' && c != '>' && c != '=' && c != '(' && c != ')') {
				pos++;

				if (!Character.isWhitespace(c)) {
					end = pos;
				}

				c = filterChars[pos];
			}

			int length = end - begin;

			if (length == 0) {
				throw new InvalidSyntaxException(NLS.bind(Msg.FILTER_MISSING_ATTR, filterstring.substring(pos)), filterstring);
			}

			return new String(filterChars, begin, length);
		}

		private String parse_value() throws InvalidSyntaxException {
			StringBuilder sb = new StringBuilder(filterChars.length - pos);

			parseloop: while (true) {
				char c = filterChars[pos];

				switch (c) {
					case ')' : {
						break parseloop;
					}

					case '(' : {
						throw new InvalidSyntaxException(NLS.bind(Msg.FILTER_INVALID_VALUE, filterstring.substring(pos)), filterstring);
					}

					case '\\' : {
						pos++;
						c = filterChars[pos];
						/* fall through into default */
					}

					default : {
						sb.append(c);
						pos++;
						break;
					}
				}
			}

			if (sb.length() == 0) {
				throw new InvalidSyntaxException(NLS.bind(Msg.FILTER_MISSING_VALUE, filterstring.substring(pos)), filterstring);
			}

			return sb.toString();
		}

		private Object parse_substring() throws InvalidSyntaxException {
			StringBuilder sb = new StringBuilder(filterChars.length - pos);

			List<String> operands = new ArrayList<>(10);

			parseloop: while (true) {
				char c = filterChars[pos];

				switch (c) {
					case ')' : {
						if (sb.length() > 0) {
							operands.add(sb.toString());
						}

						break parseloop;
					}

					case '(' : {
						throw new InvalidSyntaxException(NLS.bind(Msg.FILTER_INVALID_VALUE, filterstring.substring(pos)), filterstring);
					}

					case '*' : {
						if (sb.length() > 0) {
							operands.add(sb.toString());
						}

						sb.setLength(0);

						operands.add(null);
						pos++;

						break;
					}

					case '\\' : {
						pos++;
						c = filterChars[pos];
						/* fall through into default */
					}

					default : {
						sb.append(c);
						pos++;
						break;
					}
				}
			}

			int size = operands.size();

			if (size == 0) {
				return ""; //$NON-NLS-1$
			}

			if (size == 1) {
				String single = operands.get(0);

				if (single != null) {
					return single;
				}
			}

			return operands.toArray(new String[0]);
		}

		private void skipWhiteSpace() {
			for (int length = filterChars.length; (pos < length) && Character.isWhitespace(filterChars[pos]);) {
				pos++;
			}
		}
	}

	/**
	 * This Map is used for key lookup during filter
	 * evaluation. This Map implementation only supports the get operation using
	 * a String key as no other operations are used by the Filter
	 * implementation.
	 */
	private static final class DictionaryMap extends AbstractMap<String, Object> implements Map<String, Object> {
		static Map<String, ?> asMap(Dictionary<String, ?> dictionary) {
			if (dictionary instanceof Map) {
				@SuppressWarnings("unchecked")
				Map<String, ?> coerced = (Map<String, ?>) dictionary;
				return coerced;
			}
			return new DictionaryMap(dictionary);
		}

		private final Dictionary<String, ?> dictionary;

		/**
		 * Create a case insensitive map from the specified dictionary.
		 * 
		 * @param dictionary
		 * @throws IllegalArgumentException If {@code dictionary} contains case
		 *             variants of the same key name.
		 */
		DictionaryMap(Dictionary<String, ?> dictionary) {
			this.dictionary = requireNonNull(dictionary);
		}

		@Override
		public Object get(Object key) {
			return dictionary.get(key);
		}

		@Override
		public Set<Entry<String, Object>> entrySet() {
			throw new UnsupportedOperationException();
		}
	}

	/**
	 * This Map is used for key lookup from a ServiceReference during filter
	 * evaluation. This Map implementation only supports the get operation using
	 * a String key as no other operations are used by the Filter
	 * implementation.
	 */
	private static final class ServiceReferenceMap extends AbstractMap<String, Object> implements Map<String, Object> {
		static Map<String, ?> asMap(ServiceReference<?> reference) {
			if (reference instanceof ServiceReferenceImpl) {
				return ((ServiceReferenceImpl<?>) reference).getRegistration().getProperties();
			}
			return new ServiceReferenceMap(reference);
		}

		private final ServiceReference<?> reference;

		ServiceReferenceMap(ServiceReference<?> reference) {
			this.reference = requireNonNull(reference);
		}

		@Override
		public Object get(Object key) {
			return reference.getProperty((String) key);
		}

		@Override
		public Set<Entry<String, Object>> entrySet() {
			throw new UnsupportedOperationException();
		}
	}

	static class Range {
		private char leftRule = 0;
		private Version leftVersion;
		private Version rightVersion;
		private char rightRule = 0;
		private Collection<Version> excludes = new ArrayList<>(0);

		@Override
		public String toString() {
			if (rightVersion == null) {
				return leftVersion.toString();
			}
			return leftRule + leftVersion.toString() + ',' + rightVersion.toString() + rightRule;
		}

		void addExclude(Version exclude) {
			this.excludes.add(exclude);
			setLeft(leftRule, leftVersion);
			setRight(rightRule, rightVersion);
		}

		boolean setLeft(char leftRule, Version leftVersion) {
			if (this.leftVersion != null && this.leftVersion != leftVersion)
				return false;
			this.leftRule = excludes.contains(leftVersion) ? '(' : leftRule;
			this.leftVersion = leftVersion;
			return true;
		}

		boolean setRight(char rightRule, Version rightVersion) {
			if (this.rightVersion != null && this.rightVersion != rightVersion)
				return false;
			this.rightRule = excludes.contains(rightVersion) ? ')' : rightRule;
			this.rightVersion = rightVersion;
			return true;
		}
	}

}
