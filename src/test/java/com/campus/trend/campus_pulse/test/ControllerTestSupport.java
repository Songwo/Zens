package com.campus.trend.campus_pulse.test;

import com.campus.trend.campus_pulse.common.exception.GlobalExceptionHandler;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.security.AuthUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

public final class ControllerTestSupport {

    private ControllerTestSupport() {
    }

    public static MockMvc standaloneWithValidation(Object controller) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    public static void mockLoginUser(String userId) {
        mockLoginUser(userId, "tester", "ROLE_USER");
    }

    public static void mockLoginUser(String userId, String username, String role) {
        User user = new User();
        user.setId(userId);
        user.setUsername(username);
        user.setRole(role);
        AuthUser authUser = new AuthUser(user);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(authUser, null, authUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    public static void clearSecurity() {
        SecurityContextHolder.clearContext();
    }
}
