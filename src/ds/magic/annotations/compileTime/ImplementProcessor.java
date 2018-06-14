package ds.magic.annotations.compileTime;

import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
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

@SupportedAnnotationTypes({"ds.magic.annotations.compileTime.Implement"})
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
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env)
	{
		Set<? extends Element> annotatedElements = env.getElementsAnnotatedWith(Implement.class);
		for(Element annotated : annotatedElements)
		{
			Implement annotation = annotated.getAnnotation(Implement.class);
			TypeMirror interfaceMirror = getValueMirror(annotation);
			TypeElement interfaceType = asTypeElement(interfaceMirror);
			
			// 1. Is annotation value an interface?
			if (interfaceType.getKind() != ElementKind.INTERFACE)
			{
				String name = Implement.class.getSimpleName();
				printError("Value of @" + name + " must be an interface", annotated);
				continue;
			}
			
			//2. Is class implements interface (value of annotation)
			TypeElement enclosingType = (TypeElement)annotated.getEnclosingElement();
			if (!typeUtils.isSubtype(enclosingType.asType(), interfaceMirror))
			{
				Name className = enclosingType.getSimpleName();
				Name interfaceName = interfaceType.getSimpleName();
				printError(className + " must implemet " + interfaceName, annotated);
				continue;
			}
			
			//3. Is interface have same method as annotated
			if (!haveMethod(interfaceType, (ExecutableElement)annotated))
			{
				Name name = interfaceType.getSimpleName();
				printError(name + " don't have \"" + annotated + "\" method", annotated);
				continue;
			}
		}
		
		return false;
	}
	
	private boolean haveMethod(TypeElement interfaceType, ExecutableElement method)
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
				TypeMirror interfaceReturnType = interfaceMethod.getReturnType();
				if (!isTypeVariable(interfaceReturnType) && !returnType.equals(interfaceReturnType)) {
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
			if (haveMethod(base, method)) {
				return true;
			}
		}
		
		return false;
	}
	
	private boolean isParametersEquals(List<? extends VariableElement> methodParameters,
			List<? extends VariableElement> interfaceParameters)
	{
		if (methodParameters.size() != interfaceParameters.size()) {
			return false;
		}
		
		for (int i = 0; i < methodParameters.size(); i++)
		{
			TypeMirror interfaceParameterMirror = interfaceParameters.get(i).asType();
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
	
	private void printError(String message, Element annotatedElement)
	{
		Messager messager = processingEnv.getMessager();
		messager.printMessage(Kind.ERROR, message, annotatedElement);
	}
}
