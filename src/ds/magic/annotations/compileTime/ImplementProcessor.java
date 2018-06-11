package ds.magic.annotations.compileTime;

import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;

@SupportedAnnotationTypes(value = {"ds.magic.annotations.compileTime.Implement"})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class ImplementProcessor extends AbstractProcessor
{
	private Types typeUtils;
	
	@Override
	public void init(ProcessingEnvironment procEnv)
	{
		super.init(procEnv);
		typeUtils = this.processingEnv.getTypeUtils();
	}
	
	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
	{
		Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(Implement.class);
		for(Element annotatedElement : annotatedElements)
		{
			Implement annotation = annotatedElement.getAnnotation(Implement.class);
			TypeMirror interfaceMirror = getValueMirror(annotation);
			TypeElement interfaceType = asTypeElement(interfaceMirror);
			
			// 1. Is annotation value an interface?
			if (interfaceType.getKind() != ElementKind.INTERFACE)
			{
				printError("Value of @" + Implement.class.getSimpleName() + " must be an interface", annotatedElement);
				continue;
			}
			
			//2. Is class implements interface (value of annotation)
			TypeElement enclosingType = (TypeElement)annotatedElement.getEnclosingElement();
			if (!typeUtils.isSubtype(enclosingType.asType(), interfaceMirror))
			{
				printError(enclosingType.getSimpleName() + " must implemet " + interfaceType.getSimpleName(), annotatedElement);
				continue;
			}
			
			//3. Is interface have same method as annotated
			if (!isInterfaceHaveMethod(interfaceType, (ExecutableElement)annotatedElement))
			{
				printError(interfaceType.getSimpleName() + " don't have \"" + annotatedElement + "\" method", annotatedElement);
				continue;
			}
		}
		
		return false;
	}
	
	private boolean isInterfaceHaveMethod(TypeElement interfaceType, ExecutableElement method)
	{
		Name methodName = method.getSimpleName();
		for (Element interfaceElement : interfaceType.getEnclosedElements())
		{
			if (interfaceElement instanceof ExecutableElement)
			{
				ExecutableElement interfaceMethod = (ExecutableElement)interfaceElement;
				
				// Is names match?
				if (!interfaceMethod.getSimpleName().equals(methodName)) {
					continue;
				}
				
				// Is return types match (ignore type variable)?
				TypeMirror returnType = method.getReturnType();
				if (isTypeVariable(returnType) && !returnType.equals(interfaceMethod.getReturnType())) {
					continue;
				}
				
				// Is parameters match?
				if (!isParametersEquals(method.getParameters(), interfaceMethod.getParameters())) {
					continue;
				}
				return true;
			}
		}
		
		// Recursive search
		for (TypeMirror baseMirror : interfaceType.getInterfaces())
		{
			TypeElement base = asTypeElement(baseMirror);
			if (isInterfaceHaveMethod(base, method)) {
				return true;
			}
		}
		
		return false;
	}
	
	private boolean isParametersEquals(List<? extends VariableElement> methodParameters, List<? extends VariableElement> interfaceMethodParameters)
	{
		if (methodParameters.size() != interfaceMethodParameters.size()) {
			return false;
		}
		
		for (int i = 0; i < methodParameters.size(); i++)
		{
			TypeMirror interfaceParameterMirror = interfaceMethodParameters.get(i).asType();
			if (isTypeVariable(interfaceParameterMirror)) {
				continue;
			}
			
			if (!methodParameters.get(i).asType().equals(interfaceParameterMirror)) {
				return false;
			}
		}
		return true;
	}
	
	//https://stackoverflow.com/questions/7687829/java-6-annotation-processing-getting-a-class-from-an-annotation
	private TypeMirror getValueMirror(Implement annotation)
	{
		try {
			annotation.value();
		} catch(MirroredTypeException e) {
			return e.getTypeMirror();
		}
		return null;
	}
	
	private boolean isTypeVariable(TypeMirror type) {
		return type.getKind() == TypeKind.TYPEVAR;
	}
	
	private TypeElement asTypeElement(TypeMirror typeMirror) {
		return (TypeElement)typeUtils.asElement(typeMirror);
	}
	
	private void printError(String message, Element annotatedElement) {
		processingEnv.getMessager().printMessage(Kind.ERROR, message, annotatedElement);
	}
}
