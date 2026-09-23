package org.conjur.jenkins.jwtauth;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class JwtAuthenticationServiceTest {

    @Test
    public void testGetUrlName() {
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
    public void testGetJwkSet() throws IOException {
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