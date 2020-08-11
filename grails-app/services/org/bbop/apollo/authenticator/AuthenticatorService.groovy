package org.bbop.apollo.authenticator

import org.apache.shiro.authc.UsernamePasswordToken

import javax.servlet.http.HttpServletRequest

/**
 * Created by nathandunn on 6/30/16.
 */
interface AuthenticatorService {

    def authenticate(HttpServletRequest request)
    def authenticate(UsernamePasswordToken usernamePasswordToken,HttpServletRequest request)
    Boolean requiresToken()
}