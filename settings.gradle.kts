// settings.gradle.kts — Gradle 프로젝트 설정 및 툴체인(JDK 21) 자동 프로비저닝 구성
plugins {
	// 빌드 머신에 JDK 21이 없으면 자동으로 내려받도록 foojay 리졸버를 사용한다
	// (본 개발 환경은 JDK 23이 설치되어 있으나 PRD 기준 JDK 21로 빌드)
	id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "jw-gasul"
