package org.conjur.jenkins.jwtauth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtAuthenticationServiceTest {

    @Test
    void testGetUrlName() {
        JwtAuthenticationService authService = new JwtAuthenticationService() {

            @Override
            public String getJwkSet() {
                return null;
            }

            @Override
            public String getIconFileName() {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public String getDisplayName() {
                // TODO Auto-generated method stub
                return null;
            }
        };

        assertEquals("jwtauth", authService.getUrlName());
    }


    @Test
    void testGetJwkSet() throws Exception {
        JwtAuthenticationService authService = new JwtAuthenticationService() {

            @Override
            public String getJwkSet() {
                return "Mocked JWK Set";
            }

            @Override
            public String getIconFileName() {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public String getDisplayName() {
                // TODO Auto-generated method stub
                return null;
            }
        };

        String result = authService.getJwkSet();

        assertEquals("Mocked JWK Set", result);
    }

}