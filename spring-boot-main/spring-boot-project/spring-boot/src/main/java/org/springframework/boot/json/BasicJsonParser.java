/*
 * Copyright 2012-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Really basic JSON parser for when you have nothing else available. Comes with some
 * limitations with respect to the JSON specification (e.g. only supports String values),
 * so users will probably prefer to have a library handle things instead (Jackson or Snake
 * YAML are supported).
 * 翻译过来： 真正基本的 JSON 解析器，适用于没有其他可用内容时。在 JSON 规范方面存在一些限制（例如，仅支持 String 值），
 * 因此用户可能更愿意让库来处理事情（支持 Jackson 或 Snake YAML）
 *
 * @author Dave Syer
 * @author Jean de Klerk
 * @author Stephane Nicoll
 * @since 1.2.0
 * @see JsonParserFactory
 */
public class BasicJsonParser extends AbstractJsonParser {

	private static final int MAX_DEPTH = 1000;

	@Override
	public Map<String, Object> parseMap(String json) {
		return tryParse(() -> parseMap(json, (jsonToParse) -> parseMapInternal(0, jsonToParse)), Exception.class);
	}

	@Override
	public List<Object> parseList(String json) {
		return tryParse(() -> parseList(json, (jsonToParse) -> parseListInternal(0, jsonToParse)), Exception.class);
	}


	/**
	 * 将json字符串转换成 List 示例 ： ["xx1","xx2"]    转换成 List<String>  ,
	 * 文档写着仅支持String类型，
	 * 但是看了代码，也支持 Long 、Double
	 * @param nesting
	 * @param json
	 * @return
	 */
	private List<Object> parseListInternal(int nesting, String json) {
		List<Object> list = new ArrayList<>();
		json = trimLeadingCharacter(trimTrailingCharacter(json, ']'), '[').trim();
		for (String value : tokenize(json)) {
			list.add(parseInternal(nesting + 1, value));
		}
		return list;
	}

	/**
	 * 1.如果json 中存在 [ 调用转 parseListInternal方法 转List
	 * 2.如果json 中存在 { 调用转 parseMapInternal 转Map
	 * 3.如果前面带有 “ 就去掉前面的 ” 和 后面的 “   ，返回String  如 "xxx" ,返回 xxx
	 * 4.前面都不满足，将josn转换成Long , 转换失败，强转成 Double
	 * 5.以上都不满足或者转换失败，直接返回json字符串
	 * @param nesting
	 * @param json
	 * @return
	 */
	private Object parseInternal(int nesting, String json) {
		if (nesting > MAX_DEPTH) {
			throw new IllegalStateException("JSON is too deeply nested");
		}
		if (json.startsWith("[")) {
			return parseListInternal(nesting + 1, json);
		}
		if (json.startsWith("{")) {
			return parseMapInternal(nesting + 1, json);
		}
		if (json.startsWith("\"")) {
			return trimTrailingCharacter(trimLeadingCharacter(json, '"'), '"');
		}
		try {
			return Long.valueOf(json);
		}
		catch (NumberFormatException ex) {
			// ignore
		}
		try {
			return Double.valueOf(json);
		}
		catch (NumberFormatException ex) {
			// ignore
		}
		return json;
	}

	/**
	 * 将json字符串解析成Map
	 * @param nesting
	 * @param json
	 * @return
	 */
	private Map<String, Object> parseMapInternal(int nesting, String json) {
		Map<String, Object> map = new LinkedHashMap<>();
		// 将一个json去掉前面的 {  和 后面的 }    {  "id": 1, "name": "张三",   "age": 30 }
		json = trimLeadingCharacter(trimTrailingCharacter(json, '}'), '{').trim();
		// 经上面一步得出  "id": 1, "name": "张三",   "age": 30
		//经过tokenize方法， pair 的值是示例 ："id": 1
		for (String pair : tokenize(json)) {
			// 将"id": 1  拆分成  "id"   1 两个
			String[] values = StringUtils.trimArrayElements(StringUtils.split(pair, ":"));

			Assert.state(values[0].startsWith("\"") && values[0].endsWith("\""),
					"Expecting double-quotes around field names");
			// 将values[0] 也就是 "id"  去掉 “ 得出结果为  id
			String key = trimLeadingCharacter(trimTrailingCharacter(values[0], '"'), '"');
			// 将 values[1] 也就是 1 转成相应的基本类型，看parseInternal方法注明
			Object value = parseInternal(nesting, values[1]);

			map.put(key, value);
		}
		return map;
	}

	/**
	 *    string 的最后一位和 c 相同 ， 将 string截取，不要最后一位
	 * @param string
	 * @param c
	 * @return
	 */
	private static String trimTrailingCharacter(String string, char c) {
		if (!string.isEmpty() && string.charAt(string.length() - 1) == c) {
			return string.substring(0, string.length() - 1);
		}
		return string;
	}

	/**
	 *   string 的第一位和 c 相同 ， 将 string截取，不要第一位
	 * @param string
	 * @param c
	 * @return
	 */

	private static String trimLeadingCharacter(String string, char c) {
		if (!string.isEmpty() && string.charAt(0) == c) {
			return string.substring(1);
		}
		return string;
	}

	/**
	 *   1.示例 {  "id": 1, "name": "张三",   "age": 30 }
	 *   将所有的key-val对加进List 里面返回， 如： "id": 1   "name": "张三"   "age": 30  注：包括“  ：
	 *   注：我估计parseListInternal 方法不支持List<Object> 不支持 Object ，只支持 基本类型
	 *   不支持复杂数据对象的解析
	 *   经证实： 看了类注释确实不支持复杂类型数据
	 *   2.示例 ： ["xx1","xx2"]    一个String数组
	 *   会把每个字符串加进 List  ， 如 ； xx1  , xx2
	 * @param json
	 * @return
	 */
	private List<String> tokenize(String json) {
		List<String> list = new ArrayList<>();
		int index = 0;
		int inObject = 0;
		int inList = 0;
		boolean inValue = false;
		boolean inEscape = false;
		StringBuilder build = new StringBuilder();

		// 遍历每一个字符
		while (index < json.length()) {
			char current = json.charAt(index);

			// current == '\\'   inEscape = true
			// 带斜杠json 示例 {\"GivenName\":\"sduie\"}
			if (inEscape) {
				// 将遍历的字符加入 build
				build.append(current);
				index++;
				inEscape = false;
				continue;
			}
			if (current == '{') {
				inObject++;
			}
			if (current == '}') {
				inObject--;
			}
			if (current == '[') {
				inList++;
			}
			if (current == ']') {
				inList--;
			}
			if (current == '"') {
				// 在遍历到 "id": 1  前面的 "  inValue 该值变为true 遍历到后面的 "  inValue 该值变为false
				// 示例 {  "id": 1, "name": "张三",   "age": 30 }
				inValue = !inValue;
			}

			if (current == ',' && inObject == 0 && inList == 0 && !inValue) {
				// current == ，   这一句代码主要就是 current ==  ，  就是读到了 ，号， 读完了一对key-val 串
				// 示例 {  "id": 1, "name": "张三",   "age": 30 }
				//
				list.add(build.toString());
				build.setLength(0);
			}
			else if (current == '\\') {
				// 带斜杠json 示例 {\"GivenName\":\"sduie\"}

				inEscape = true;
			}
			else {
				build.append(current);
			}
			index++;
		}
		if (!build.isEmpty()) {
			list.add(build.toString().trim());
		}
		return list;
	}

}
