package io.github.tusharsri.blueprint.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tusharsri.blueprint.support.ArchitectureCheck;
import io.github.tusharsri.blueprint.util.ApiConstants;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Architecture rules enforced as plain tests (no extra library): layering, injection style, and DTO design.
 * They scan the compiled main classes, so a violation fails the build.
 */
@ArchitectureCheck
class ArchitectureTest {

    private static final String ROOT = ArchitectureTest.class.getPackageName().replaceFirst("\\.architecture$", "");
    private static final List<String> MAIN_PACKAGES = List.of("config", "controller", "demo", "exception",
            "idempotency", "repository", "security", "service");

    private static List<Class<?>> scan(String basePackage, boolean everyClass, Class<? extends Annotation> annotation) {
        ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                return true;
            }
        };
        if (everyClass) {
            provider.addIncludeFilter((reader, factory) -> true);
        } else {
            provider.addIncludeFilter(new AnnotationTypeFilter(annotation));
        }
        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition definition : provider.findCandidateComponents(basePackage)) {
            try {
                classes.add(Class.forName(definition.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
        return classes;
    }

    private static List<Class<?>> dependenciesOf(Class<?> type) {
        List<Class<?>> dependencies = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            dependencies.add(field.getType());
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            dependencies.addAll(Arrays.asList(constructor.getParameterTypes()));
        }
        return dependencies;
    }

    @Test
    void controllers_live_in_the_controller_or_demo_package() {
        List<Class<?>> controllers = scan(ROOT, false, RestController.class);

        assertThat(controllers).isNotEmpty();
        assertThat(controllers).allSatisfy(c -> assertThat(c.getPackageName())
                .isIn(ROOT + ".controller", ROOT + ".demo"));
    }

    @Test
    void controllers_never_depend_on_repositories() {
        for (Class<?> controller : scan(ROOT, false, RestController.class)) {
            assertThat(dependenciesOf(controller))
                    .as("dependencies of %s", controller.getSimpleName())
                    .noneMatch(d -> d.getPackageName().startsWith(ROOT + ".repository"));
        }
    }

    @Test
    void services_never_depend_on_controllers() {
        for (Class<?> service : scan(ROOT, false, Service.class)) {
            assertThat(dependenciesOf(service))
                    .as("dependencies of %s", service.getSimpleName())
                    .noneMatch(d -> d.getPackageName().startsWith(ROOT + ".controller"));
        }
    }

    @Test
    void controller_endpoints_never_expose_domain_model_objects() {
        for (Class<?> controller : scan(ROOT, false, RestController.class)) {
            for (Method method : controller.getDeclaredMethods()) {
                if (Modifier.isPublic(method.getModifiers()) && !method.isSynthetic()) {
                    assertThat(method.getGenericReturnType().getTypeName())
                            .as("return type of %s.%s", controller.getSimpleName(), method.getName())
                            .doesNotContain(ROOT + ".model.");
                }
            }
        }
    }

    @Test
    void all_controllers_are_mounted_under_the_api_base_path() {
        for (Class<?> controller : scan(ROOT, false, RestController.class)) {
            RequestMapping mapping = controller.getAnnotation(RequestMapping.class);
            assertThat(mapping).as("@RequestMapping on %s", controller.getSimpleName()).isNotNull();
            assertThat(mapping.value()).anyMatch(path -> path.startsWith(ApiConstants.BASE_PATH));
        }
    }

    @Test
    void dtos_are_immutable_records() {
        List<Class<?>> dtos = scan(ROOT + ".dto", true, null);

        assertThat(dtos).isNotEmpty();
        assertThat(dtos).allSatisfy(dto -> assertThat(dto.isRecord()).as("%s is a record", dto.getSimpleName()).isTrue());
    }

    @Test
    void application_code_uses_constructor_injection_only() {
        for (String pkg : MAIN_PACKAGES) {
            for (Class<?> type : scan(ROOT + "." + pkg, true, null)) {
                assertThat(Arrays.stream(type.getDeclaredFields()).filter(f -> f.isAnnotationPresent(Autowired.class)))
                        .as("@Autowired fields in %s", type.getSimpleName())
                        .isEmpty();
            }
        }
    }
}
