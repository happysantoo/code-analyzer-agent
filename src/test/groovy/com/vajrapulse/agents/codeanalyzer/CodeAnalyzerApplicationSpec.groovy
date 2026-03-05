package com.vajrapulse.agents.codeanalyzer

import spock.lang.Specification

class CodeAnalyzerApplicationSpec extends Specification {

    def "application main class is loadable"() {
        when:
        def clazz = CodeAnalyzerApplication
        then:
        clazz != null
        clazz.simpleName == "CodeAnalyzerApplication"
    }

    def "getApplicationName returns app name"() {
        expect:
        AppInfo.getApplicationName() == "code-analyzer-agent"
    }
}
