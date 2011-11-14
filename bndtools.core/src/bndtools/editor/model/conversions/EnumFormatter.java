package bndtools.editor.model.conversions;

/**
 * Formats an enum type. Outputs {@code null} when the value of the enum is
 * equal to a default value.
 *
 * @param <E>
 * @author Neil Bartlett
 */
public class EnumFormatter<E extends Enum<E>> implements Converter<String, E> {

    private final Class<E> enumType;
    private final E defaultValue;

    /**
     * Construct a new formatter with no default value, i.e. any non-null value
     * of the enum will print that value.
     *
     * @param enumType
     *            The enum type.
     * @return
     */
    public static <E extends Enum<E>> EnumFormatter<E> create(Class<E> enumType) {
        return new EnumFormatter<E>(enumType, null);
    }

    /**
     * Construct a new formatter with the specified default value.
     *
     * @param enumType
     *            The enum type.
     * @param defaultValue
     *            The default value, which will never be output.
     * @return
     */
    public static <E extends Enum<E>> EnumFormatter<E> create(Class<E> enumType, E defaultValue) {
        return new EnumFormatter<E>(enumType, defaultValue);
    }

    private EnumFormatter(Class<E> enumType, E defaultValue) {
        this.enumType = enumType;
        this.defaultValue = defaultValue;
    }

    public String convert(E input) throws IllegalArgumentException {
        String result;
        if (input == defaultValue)
            result = null;
        else
            result = input != null ? input.toString() : null;
        return result;
    }

}
