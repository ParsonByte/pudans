GRADLE ?= ./gradlew

SHELL := /bin/sh

.DEFAULT_GOAL := help

.PHONY: help build compile test test-one check api-docs clean format-check verify

## 列出所有目标及用途
help:
	@awk 'BEGIN { FS = ":"; print "可用目标：" } \
		/^## / { desc = substr($$0, 4); next } \
		/^[a-zA-Z0-9_-]+:/ { if (desc != "") { printf "  %-14s %s\n", $$1, desc; desc = "" } }' $(MAKEFILE_LIST)

## 构建后端（编译、测试并打包）
build:
	$(GRADLE) :appkernel:build

## 编译后端主代码
compile:
	$(GRADLE) :appkernel:compileJava

## 运行后端测试
test:
	$(GRADLE) :appkernel:test

## 运行单个测试（用法：make test-one TEST=全限定类名）
test-one:
	@if [ -z "$(TEST)" ]; then \
		printf '%s\n' '错误：请通过 TEST 指定测试类，例如 make test-one TEST=cn.tolim.pudans.ExampleTest' >&2; \
		exit 1; \
	fi
	$(GRADLE) :appkernel:test --tests "$(TEST)"

## 编译、运行测试并校验 OpenAPI
check: compile test api-docs

## 校验 OpenAPI 文档
api-docs:
	$(GRADLE) :api-docs:validateOpenApiDocs

## 清理构建产物
clean:
	$(GRADLE) clean

## 检查 git diff 中的空白错误
format-check:
	git diff --check

## 本地提交前校验（check 加 format-check）
verify: check format-check
