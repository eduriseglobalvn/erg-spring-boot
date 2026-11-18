@org.springframework.modulith.ApplicationModule(
        displayName = "School",
        allowedDependencies = {"organization", "lcms :: persistence", "lms :: persistence", "users :: persistence", "curriculum :: persistence"})
package com.erg.ergspringboot.school;
