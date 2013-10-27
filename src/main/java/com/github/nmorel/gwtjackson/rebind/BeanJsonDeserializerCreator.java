package com.github.nmorel.gwtjackson.rebind;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.nmorel.gwtjackson.client.stream.JsonToken;
import com.google.gwt.core.ext.GeneratorContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.typeinfo.JAbstractMethod;
import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.JConstructor;
import com.google.gwt.core.ext.typeinfo.JMethod;
import com.google.gwt.core.ext.typeinfo.JParameter;
import com.google.gwt.thirdparty.guava.common.base.Function;
import com.google.gwt.thirdparty.guava.common.base.Joiner;
import com.google.gwt.thirdparty.guava.common.collect.Collections2;
import com.google.gwt.user.rebind.SourceWriter;

import static com.github.nmorel.gwtjackson.rebind.CreatorUtils.QUOTED_FUNCTION;
import static com.github.nmorel.gwtjackson.rebind.CreatorUtils.getDefaultValueForType;

/**
 * @author Nicolas Morel
 */
public class BeanJsonDeserializerCreator extends AbstractBeanJsonCreator {

    private static final String INSTANCE_BUILDER_CLASS = "com.github.nmorel.gwtjackson.client.deser.bean.InstanceBuilder";

    private static final String INSTANCE_CLASS = "com.github.nmorel.gwtjackson.client.deser.bean.Instance";

    private static final Function<String, String> FORMAT_VARIABLE = new Function<String, String>() {
        @Override
        public String apply( @Nullable String s ) {
            if ( null == s ) {
                return null;
            }
            return "v_" + s;
        }
    };

    private static final String INSTANCE_BUILDER_DESERIALIZER_FORMAT = "deserializer_%s";

    private static final String BEAN_PROPERTY_DESERIALIZER_CLASS = "com.github.nmorel.gwtjackson.client.deser.bean" + "" +
        ".BeanPropertyDeserializer";

    private static final String BACK_REFERENCE_PROPERTY_BEAN_CLASS = "com.github.nmorel.gwtjackson.client.deser.bean.BackReferenceProperty";

    private static final String SUPERCLASS_DESERIALIZATION_INFO_CLASS = "com.github.nmorel.gwtjackson.client.deser.bean" + "" +
        ".SuperclassDeserializationInfo";

    private static final String SUBTYPE_DESERIALIZER_CLASS = "com.github.nmorel.gwtjackson.client.deser.bean.SubtypeDeserializer";

    public BeanJsonDeserializerCreator( TreeLogger logger, GeneratorContext context, JacksonTypeOracle typeOracle ) {
        super( logger, context, typeOracle );
    }

    @Override
    protected boolean isSerializer() {
        return false;
    }

    @Override
    protected void writeClassBody( SourceWriter source, BeanInfo beanInfo, Map<String,
        PropertyInfo> properties ) throws UnableToCompleteException {
        source.println();

        TypeParameters typeParameters = generateTypeParameterMapperFields( source, beanInfo, JSON_DESERIALIZER_CLASS,
            TYPE_PARAMETER_DESERIALIZER_FIELD_NAME );

        if ( null != typeParameters ) {
            source.println();
        }

        generateConstructors( source, beanInfo, properties, typeParameters );

        source.println();

        source.println();
        generateAdditionalMethods( source, properties );

        source.commit( logger );
    }

    private void generateConstructors( SourceWriter source, BeanInfo beanInfo, Map<String, PropertyInfo> properties,
                                       TypeParameters typeParameters ) throws UnableToCompleteException {
        source.print( "public %s(", getSimpleClassName() );
        if ( null != typeParameters ) {
            source.print( typeParameters.getJoinedTypeParameterMappersWithType() );
        }
        source.println( ") {" );
        source.indent();
        source.print( "this(" );
        if ( null != typeParameters ) {
            source.print( "%s, ", typeParameters.getJoinedTypeParameterMappersWithoutType() );
        }
        source.println( "null, null);" );
        source.outdent();
        source.println( "}" );

        source.println();

        source.print( "public %s(", getSimpleClassName() );
        if ( null != typeParameters ) {
            source.print( "%s, ", typeParameters.getJoinedTypeParameterMappersWithType() );
        }
        source
            .println( "%s<?> idProperty, %s<%s> superclassInfo) {", IDENTITY_DESERIALIZATION_INFO_CLASS,
                SUPERCLASS_DESERIALIZATION_INFO_CLASS, beanInfo
                .getType().getParameterizedQualifiedSourceName() );
        source.indent();
        source.println( "super();" );

        source.println();

        if ( null != typeParameters ) {
            for ( String parameterizedDeserializer : typeParameters.getTypeParameterMapperNames() ) {
                source.println( "this.%s = %s%s;", parameterizedDeserializer, TYPE_PARAMETER_PREFIX, parameterizedDeserializer );
            }
            source.println();
        }

        if ( beanInfo.isInstantiable() ) {
            source.print( "setInstanceBuilder(" );
            generateInstanceBuilderClass( source, beanInfo, properties );
            source.println( ");" );
            source.println();
        }

        if ( null != beanInfo.getIdentityInfo() ) {
            source.println( "if(null == idProperty) {" );
            source.indent();
            source.print( "setIdentityInfo(" );
            generateIdentifierDeserializationInfo( source, beanInfo.getIdentityInfo() );
            source.println( ");" );
            source.outdent();
            source.println( "} else {" );
        } else {
            source.println( "if(null != idProperty) {" );
        }
        source.indent();
        source.println( "setIdentityInfo(idProperty);" );
        source.outdent();
        source.println( "}" );

        if ( beanInfo.isHasSubtypes() ) {
            source.println( "if(null == superclassInfo) {" );
            source.indent();
            source.print( "setSuperclassInfo(" );
            generateDefaultSuperclassInfo( source, beanInfo );
            source.println( ");" );
            source.outdent();
            source.println( "} else {" );
        } else {
            source.println( "if(null != superclassInfo) {" );
        }
        source.indent();
        source.println( "setSuperclassInfo(superclassInfo);" );
        source.outdent();
        source.println( "}" );

        source.println();

        if ( beanInfo.isInstantiable() ) {
            generatePropertyDeserializers( source, beanInfo, properties );
        }

        source.outdent();
        source.println( "}" );
    }

    private void generateInstanceBuilderClass( SourceWriter source, BeanInfo beanInfo, Map<String,
        PropertyInfo> properties ) throws UnableToCompleteException {

        source.println( "new %s<%s>() {", INSTANCE_BUILDER_CLASS, beanInfo.getType().getParameterizedQualifiedSourceName() );
        source.indent();

        if ( null != beanInfo.getCreatorParameters() && !beanInfo.getCreatorParameters().isEmpty() ) {
            for ( Entry<String, JParameter> entry : beanInfo.getCreatorParameters().entrySet() ) {
                source.println( "private %s<%s> %s;", JSON_DESERIALIZER_CLASS, getQualifiedClassName( entry.getValue().getType() ), String
                    .format( INSTANCE_BUILDER_DESERIALIZER_FORMAT, entry.getKey() ) );
            }
            source.println();
        }

        source.println( "@Override" );
        source.println( "public %s<%s> newInstance( %s reader, %s ctx ) throws %s {", INSTANCE_CLASS, beanInfo.getType()
            .getParameterizedQualifiedSourceName(), JSON_READER_CLASS, JSON_DESERIALIZATION_CONTEXT_CLASS, IOException.class.getName() );
        source.indent();

        if ( beanInfo.isCreatorDefaultConstructor() ) {
            generateInstanceBuilderForDefaultConstructor( source, beanInfo, properties );
        } else if ( beanInfo.isCreatorDelegation() ) {
            generateInstanceBuilderForConstructorOrFactoryMethodDelegation( source, beanInfo, properties );
        } else {
            generateInstanceBuilderForConstructorOrFactoryMethod( source, beanInfo, properties );
        }

        source.outdent();
        source.println( "}" );
        source.println();

        generateInstanceBuilderCreateMethod( source, beanInfo, properties );

        source.outdent();
        source.print( "}" );
    }

    /**
     * Generate the instance builder class body for a default constructor. We directly instantiate the bean at the builder creation and we
     * set the properties to it
     *
     * @param source writer
     * @param beanInfo info on bean
     * @param properties list of properties
     */
    private void generateInstanceBuilderForDefaultConstructor( SourceWriter source, BeanInfo beanInfo, Map<String,
        PropertyInfo> properties ) {
        source.println( "return new %s<%s>(create());", INSTANCE_CLASS, beanInfo.getType().getParameterizedQualifiedSourceName(), beanInfo
            .getType().getParameterizedQualifiedSourceName() );
    }

    /**
     * Generate the instance builder class body for a constructor with parameters or factory method. We will declare all the fields and
     * instanciate the bean only on build() method when all properties have been deserialiazed
     *
     * @param source writer
     * @param info info on bean
     * @param properties list of properties
     */
    private void generateInstanceBuilderForConstructorOrFactoryMethod( SourceWriter source, BeanInfo info, Map<String,
        PropertyInfo> properties ) throws UnableToCompleteException {

        source.println( "%s<%s, %s> bufferedProperties = new %s<%s, %s>();", Map.class.getName(), String.class.getName(), String.class
            .getName(), HashMap.class.getName(), String.class.getName(), String.class.getName() );

        source.println();

        List<String> requiredProperties = new ArrayList<String>();
        for ( String name : info.getCreatorParameters().keySet() ) {
            PropertyInfo propertyInfo = properties.get( name );

            source.println( "%s %s = %s;", propertyInfo.getType().getParameterizedQualifiedSourceName(), FORMAT_VARIABLE
                .apply( name ), getDefaultValueForType( propertyInfo.getType() ) );

            if ( propertyInfo.isRequired() ) {
                requiredProperties.add( name );
            }
        }

        source.println();

        source.println( "int nbParamToFind = %d;", info.getCreatorParameters().size() );

        if ( !requiredProperties.isEmpty() ) {
            String requiredList = Joiner.on( ", " ).join( Collections2.transform( requiredProperties, QUOTED_FUNCTION ) );
            source.println( "%s<%s> requiredProperties = new %s<%s>(%s.asList(%s));", Set.class.getName(), String.class
                .getName(), HashSet.class.getName(), String.class.getName(), Arrays.class.getName(), requiredList );
        }

        source.println();

        source.println( "while (nbParamToFind > 0 && %s.NAME == reader.peek()) {", JsonToken.class.getName() );
        source.indent();

        source.println( "String name = reader.nextName();" );
        source.println();

        for ( String name : info.getCreatorParameters().keySet() ) {
            PropertyInfo propertyInfo = properties.get( name );

            source.println( "if(\"%s\".equals(name)) {", name );
            source.indent();
            source.println( "if(null == %s) {", String.format( INSTANCE_BUILDER_DESERIALIZER_FORMAT, name ) );
            source.indent();
            source.println( "%s = %s;", String
                .format( INSTANCE_BUILDER_DESERIALIZER_FORMAT, name ), getJsonDeserializerFromType( propertyInfo
                .getType(), propertyInfo ) );
            source.outdent();
            source.println( "}" );
            source.println( "%s = %s.deserialize(reader, ctx);", FORMAT_VARIABLE.apply( name ), String
                .format( INSTANCE_BUILDER_DESERIALIZER_FORMAT, name ) );
            source.println( "nbParamToFind--;" );
            if ( propertyInfo.isRequired() ) {
                source.println( "requiredProperties.remove(\"%s\");", name );
            }
            source.println( "continue;" );
            source.outdent();
            source.println( "}" );
        }

        source.println();
        source.println( "bufferedProperties.put( name, reader.nextValue() );" );

        source.outdent();
        source.println( "}" );

        source.println();

        if ( !requiredProperties.isEmpty() ) {
            source.println( "if(!requiredProperties.isEmpty()) {" );
            source.indent();
            source.println( "throw ctx.traceError( \"Required properties are missing : \" + requiredProperties, reader );" );
            source.outdent();
            source.println( "}" );
            source.println();
        }

        String parameters = Joiner.on( ", " ).join( Collections2.transform( info.getCreatorParameters().keySet(), FORMAT_VARIABLE ) );
        source.println( "return new %s<%s>( create(%s), bufferedProperties );", INSTANCE_CLASS, info.getType()
            .getParameterizedQualifiedSourceName(), parameters );
    }

    /**
     * Generate the instance builder class body for a constructor or factory method with delegation.
     *
     * @param source writer
     * @param info info on bean
     * @param properties list of properties
     */
    private void generateInstanceBuilderForConstructorOrFactoryMethodDelegation( SourceWriter source, BeanInfo info, Map<String,
        PropertyInfo> properties ) throws UnableToCompleteException {
        // FIXME @JsonCreator with delegation
        logger.log( TreeLogger.Type.ERROR, "The delegation is not supported yet" );
        throw new UnableToCompleteException();
    }

    private void generateInstanceBuilderCreateMethod( SourceWriter source, BeanInfo info, Map<String, PropertyInfo> properties ) {
        JAbstractMethod method = info.getCreatorMethod();

        StringBuilder parametersBuilder = new StringBuilder();
        StringBuilder parametersNameBuilder = new StringBuilder();
        for ( Map.Entry<String, JParameter> parameterEntry : info.getCreatorParameters().entrySet() ) {
            if ( parametersBuilder.length() > 0 ) {
                parametersBuilder.append( ", " );
                parametersNameBuilder.append( ", " );
            }
            PropertyInfo property = properties.get( parameterEntry.getKey() );

            parametersBuilder.append( property.getType().getParameterizedQualifiedSourceName() ).append( " " ).append( property
                .getPropertyName() );
            parametersNameBuilder.append( property.getPropertyName() );
        }

        if ( method.isPrivate() ) {
            // private method, we use jsni
            source.println( "private native %s create(%s) /*-{", info.getType().getParameterizedQualifiedSourceName(), parametersBuilder
                .toString() );
            source.indent();

            if ( null != method.isConstructor() ) {
                JConstructor constructor = method.isConstructor();
                source.println( "return %s(%s);", constructor.getJsniSignature(), parametersNameBuilder.toString() );
            } else {
                JMethod factory = method.isMethod();
                source.println( "return %s(%s);", factory.getJsniSignature(), parametersNameBuilder.toString() );
            }

            source.outdent();
            source.println( "}-*/;" );
        } else {
            source.println( "private %s create(%s) {", info.getType().getParameterizedQualifiedSourceName(), parametersBuilder.toString() );
            source.indent();

            if ( null != method.isConstructor() ) {
                source.println( "return new %s(%s);", info.getType().getParameterizedQualifiedSourceName(), parametersNameBuilder
                    .toString() );
            } else {
                source.println( "return %s.%s(%s);", info.getType().getQualifiedSourceName(), method.getName(), parametersNameBuilder
                    .toString() );
            }

            source.outdent();
            source.println( "}" );
        }
    }

    private void generateDefaultSuperclassInfo( SourceWriter source, BeanInfo info ) throws UnableToCompleteException {
        source.print( "new %s(", SUPERCLASS_DESERIALIZATION_INFO_CLASS );
        // gives the information about how to read and write the type info
        if ( null != info.getTypeInfo() ) {
            String typeInfoProperty = null;
            if ( JsonTypeInfo.As.PROPERTY.equals( info.getTypeInfo().include() ) ) {
                typeInfoProperty = "\"" + (info.getTypeInfo().property().isEmpty() ? info.getTypeInfo().use()
                    .getDefaultPropertyName() : info.getTypeInfo().property()) + "\"";
            }
            source.print( "com.fasterxml.jackson.annotation.JsonTypeInfo.As.%s, %s", info.getTypeInfo().include(), typeInfoProperty );
        }
        source.println( ")" );
        source.indent();

        generateSubtypeDeserializers( source, info );

        source.outdent();
    }

    private void generateSubtypeDeserializers( SourceWriter source, BeanInfo info ) throws UnableToCompleteException {
        if ( info.isInstantiable() ) {
            generateSubtypeDeserializer( source, info, info.getType() );
        }
        for ( JClassType subtype : info.getType().getSubtypes() ) {
            generateSubtypeDeserializer( source, info, subtype );
        }
    }

    private void generateSubtypeDeserializer( SourceWriter source, BeanInfo info, JClassType subtype ) throws UnableToCompleteException {
        String typeMetadata;
        if ( null == info.getTypeInfo() ) {
            typeMetadata = null;
        } else {
            typeMetadata = "\"" + extractTypeMetadata( info, subtype ) + "\"";
        }

        source.println( ".addSubtypeDeserializer( new %s<%s>() {", SUBTYPE_DESERIALIZER_CLASS, subtype.getQualifiedSourceName() );
        source.indent();
        source.indent();

        source.println( "@Override" );
        source.println( "public %s<%s> newDeserializer(%s ctx) {", ABSTRACT_BEAN_JSON_DESERIALIZER_CLASS, subtype
            .getQualifiedSourceName(), JSON_DESERIALIZATION_CONTEXT_CLASS );
        source.indent();
        String deserializer = info.getType() == subtype ? getQualifiedClassName() + ".this" : getJsonDeserializerFromType( subtype );
        source.println( "return %s;", deserializer );
        source.outdent();
        source.println( "}" );

        source.outdent();
        source.println( "}, %s.class, %s )", subtype.getQualifiedSourceName(), typeMetadata );
        source.println();

        source.outdent();
    }

    private void generatePropertyDeserializers( SourceWriter source, BeanInfo info, Map<String,
        PropertyInfo> properties ) throws UnableToCompleteException {
        for ( PropertyInfo property : properties.values() ) {
            if ( null != info.getCreatorParameters() && info.getCreatorParameters().containsKey( property.getPropertyName() ) ) {
                // properties used in constructor are deserialized inside instance builder
                continue;
            }

            if ( property.isIgnored() ) {
                // we add the name of the property to the ignoredProperties list
                source.println( "addIgnoredProperty(\"%s\");", property.getPropertyName() );
                continue;
            }

            String setterAccessor = property.getSetterAccessor();
            if ( null == setterAccessor ) {
                // there is no setter visible
                continue;
            }

            if ( null == property.getBackReference() ) {
                // this is not a back reference, we add the default deserializer
                source.println( "addProperty(\"%s\", %s, new " + BEAN_PROPERTY_DESERIALIZER_CLASS + "<%s, %s>() {", property
                    .getPropertyName(), property.isRequired(), info.getType()
                    .getParameterizedQualifiedSourceName(), getQualifiedClassName( property.getType() ) );

                source.indent();
                source.println( "@Override" );
                source.println( "protected %s<%s> newDeserializer(%s ctx) {", JSON_DESERIALIZER_CLASS, getQualifiedClassName( property
                    .getType() ), JSON_DESERIALIZATION_CONTEXT_CLASS );
                source.indent();
                source.println( "return %s;", getJsonDeserializerFromType( property.getType(), property ) );
                source.outdent();
                source.println( "}" );

                source.println( "@Override" );
                source.println( "public %s deserialize(%s reader, %s bean, %s ctx) {", getQualifiedClassName( property
                    .getType() ), JSON_READER_CLASS, info.getType()
                    .getParameterizedQualifiedSourceName(), JSON_DESERIALIZATION_CONTEXT_CLASS );
                source.indent();

                source.println( "%s<%s> deserializer = getDeserializer(ctx);", JSON_DESERIALIZER_CLASS, getQualifiedClassName( property
                    .getType() ) );
                source.println( "%s value = deserializer.deserialize(reader, ctx);", property.getType()
                    .getParameterizedQualifiedSourceName() );
                source.println( property.getSetterAccessor() + ";", "value" );
                if ( null != property.getManagedReference() ) {
                    source.println( "deserializer.setBackReference(\"%s\", bean, value, ctx);", property.getManagedReference() );
                }
                source.println( "return value;" );
            } else {
                // this is a back reference, we add the special back reference property that will be called by the parent
                source.println( "addProperty(\"%s\", new " + BACK_REFERENCE_PROPERTY_BEAN_CLASS + "<%s, %s>() {", property
                    .getBackReference(), info.getType().getParameterizedQualifiedSourceName(), getQualifiedClassName( property
                    .getType() ) );

                source.indent();
                source.println( "@Override" );
                source.println( "public void setBackReference(%s bean, %s reference, %s ctx) {", info.getType()
                    .getParameterizedQualifiedSourceName(), property.getType()
                    .getParameterizedQualifiedSourceName(), JSON_DESERIALIZATION_CONTEXT_CLASS );
                source.indent();

                source.println( property.getSetterAccessor() + ";", "reference" );
            }

            source.outdent();
            source.println( "}" );

            source.outdent();
            source.println( "} );" );
        }
    }

    private void generateAdditionalMethods( SourceWriter source, Map<String, PropertyInfo> properties ) {
        for ( PropertyInfo property : properties.values() ) {
            for ( PropertyInfo.AdditionalMethod method : property.getAdditionalDeserializationMethods() ) {
                method.write( source );
                source.println();
            }
        }
    }
}
