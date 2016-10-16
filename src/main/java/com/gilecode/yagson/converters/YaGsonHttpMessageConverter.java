package com.gilecode.yagson.converters;

import com.gilecode.yagson.YaGson;
import com.gilecode.yagson.com.google.gson.JsonIOException;
import com.gilecode.yagson.com.google.gson.JsonParseException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.Charset;

/**
 * A {@link org.springframework.http.converter.HttpMessageConverter} based on
 * <a href="http://yagson.gilecode.com">YaGson</a>, a universal types-preserving Java-JSON-Java mapper
 * with a transparent support of any kind of self-references.
 * <p/>
 * If used along with other JSON converters, it is recommended to use a dedicated "application/yagson" media type,
 * and create the converter as {@code new YaGsonHttpMessageConverter()}. Otherwise, if used for all JSON communications,
 * create it using {@code new YaGsonHttpMessageConverter(true)} and use "application/json" or other standard JSON media
 * types.
 *
 * @see <a href="https://github.com/amogilev/yagson-spring-rest-sample">yagson-spring-rest-sample</a> for the usage
 * examples
 *
 * @author Andrey Mogilev
 */
public class YaGsonHttpMessageConverter extends AbstractHttpMessageConverter<Object>
        implements GenericHttpMessageConverter<Object> {

    public static final Charset DEFAULT_CHARSET = Charset.forName("UTF-8");

    public static final MediaType MT_YAGSON = new MediaType("application", "yagson", DEFAULT_CHARSET);
    public static final MediaType MT_JSON = new MediaType("application", "json", DEFAULT_CHARSET);
    public static final MediaType MT_JSON_PLUS = new MediaType("application", "*+json", DEFAULT_CHARSET);

    private YaGson yaGson = new YaGson();


    /**
     * Construct {@code YaGsonHttpMessageConverter} which accepts and produces only the default 'application/yagson'
     * media type.
     */
    public YaGsonHttpMessageConverter() {
        this(false);
    }

    /**
     * If {@code replacesJsonConverters==true}, constructs {@code YaGsonHttpMessageConverter} which is supposed
     * to be a single JSON converter in your Spring context. In such case, it accepts all known JSON media types, and
     * produces "application/json" media type by default.
     * <p/>
     * Otherwise, if {@code replacesJsonConverters==false}, constructs {@code YaGsonHttpMessageConverter} which is
     * supposed to be a dedicated converter bound to the "application/yagson" media type. In such a case, it may be
     * used simultaneously with other JSON converters.
     */
    public YaGsonHttpMessageConverter(boolean replacesJsonConverters) {
        this(
                replacesJsonConverters ? MT_JSON : MT_YAGSON, // default type
                replacesJsonConverters ? new MediaType[]{MT_JSON_PLUS, MT_YAGSON} : new MediaType[0]
        );
    }

    /**
     * Construct a custom {@code YaGsonHttpMessageConverter} with the specified supported media types.
     *
     * @param defaultType the default supported media type, set as Content-Type to HTTP responses if
     *                    another content type is not specified
     * @param otherSupportedTypes other accepted media type, additional to 'defaultType'
     */
    public YaGsonHttpMessageConverter(MediaType defaultType, MediaType... otherSupportedTypes) {
        super(buildSupportedTypesArray(defaultType, otherSupportedTypes));
    }

    /**
     * Construct a custom {@code YaGsonHttpMessageConverter} with the specified supported media types.
     *
     * @param defaultType the default supported media type, set as Content-Type to HTTP responses if
     *                    another content type is not specified
     * @param otherSupportedTypes other accepted media type, additional to 'defaultType'
     */
    public YaGsonHttpMessageConverter(String defaultType, String... otherSupportedTypes) {
        super(buildSupportedTypesArray(defaultType, otherSupportedTypes));
    }

    /**
     * May be used to customize the used {@link YaGson} mapper.
     */
    public void setYaGson(YaGson yaGson) {
        this.yaGson = yaGson;
    }

    private static MediaType[] buildSupportedTypesArray(MediaType defaultType, MediaType[] otherSupportedTypes) {
        if (defaultType.isWildcardType() || defaultType.isWildcardSubtype()) {
            throw new IllegalStateException("The default MediaType must not contain wildcards:" + defaultType);
        }

        MediaType[] supportedTypes = new MediaType[1 + otherSupportedTypes.length];
        supportedTypes[0] = defaultType;
        System.arraycopy(otherSupportedTypes, 0, supportedTypes, 1, otherSupportedTypes.length);
        return supportedTypes;
    }

    private static MediaType[] buildSupportedTypesArray(String defaultType, String[] otherSupportedTypes) {
        MediaType[] supportedMediaTypes = new MediaType[1 + otherSupportedTypes.length];
        MediaType defaultMediaType = supportedMediaTypes[0] = MediaType.parseMediaType(defaultType);
        if (defaultMediaType.isWildcardType() || defaultMediaType.isWildcardSubtype()) {
            throw new IllegalStateException("The default MediaType must not contain wildcards:" + defaultMediaType);
        }

        for (int i = 0; i < otherSupportedTypes.length; i++) {
            supportedMediaTypes[i + 1] =  MediaType.parseMediaType(otherSupportedTypes[i]);
        }

        // make sure the charset is set
        for (int i = 0; i < otherSupportedTypes.length; i++) {
            MediaType mt = supportedMediaTypes[i];
            if (mt.getParameter("charset") == null) {
                supportedMediaTypes[i] = new MediaType(mt.getType(), mt.getSubtype(), DEFAULT_CHARSET);
            }
        }

        return supportedMediaTypes;
    }

    @Override
    protected boolean supports(Class clazz) {
        return true;
    }

    @Override
    public boolean canRead(Type type, Class<?> contextClass, MediaType mediaType) {
        return canRead(mediaType);    }

    @Override
    public boolean canRead(Class<?> clazz, MediaType mediaType) {
        return canRead(mediaType);
    }

    @Override
    public boolean canWrite(Class<?> clazz, MediaType mediaType) {
        return canWrite(mediaType);
    }

    @Override
    public Object read(Type type, Class<?> contextClass, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        return readWithType(inputMessage, type);
    }

    public boolean canWrite(Type type, Class<?> clazz, MediaType mediaType) {
        return canWrite(mediaType);
    }

    public void write(Object o, Type type, MediaType contentType, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
        writeWithType(type, o, outputMessage);
    }

    private Object readWithType(HttpInputMessage inputMessage, Type type) throws IOException {
        Reader jsonReader = new InputStreamReader(inputMessage.getBody(), getCharset(inputMessage.getHeaders()));
        try {
            return yaGson.fromJson(jsonReader, type);
        }
        catch (JsonParseException ex) {
            throw new HttpMessageNotReadableException("Could not read JSON: " + ex.getMessage(), ex);
        }
    }

    @Override
    protected Object readInternal(Class clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        return readWithType(inputMessage, clazz);
    }

    @Override
    protected void writeInternal(Object o, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
        writeWithType(Object.class, o, outputMessage);
    }

    private void writeWithType(Type type, Object o, HttpOutputMessage outputMessage) throws IOException {
        final HttpHeaders headers = outputMessage.getHeaders();
        addDefaultHeaders(headers, o, null);

        Charset charset = getCharset(outputMessage.getHeaders());
        OutputStreamWriter writer = new OutputStreamWriter(outputMessage.getBody(), charset);
        try {
            yaGson.toJson(o, type, writer);
            writer.close();
        }
        catch (JsonIOException ex) {
            throw new HttpMessageNotWritableException("Could not write JSON: " + ex.getMessage(), ex);
        }
    }

    private Charset getCharset(HttpHeaders headers) {
        MediaType contentType = headers == null ? null : headers.getContentType();
        String charSetName = contentType == null ? null : contentType.getParameter("charset");
        if (charSetName == null) {
            return DEFAULT_CHARSET;
        } else {
            return Charset.forName(charSetName);
        }
    }
}
