@org.springframework.modulith.ApplicationModule(
        displayName = "Timekeeping",
        allowedDependencies = {"calendar", "notifications", "users", "users :: persistence"})
package com.erg.ergspringboot.timekeeping;
