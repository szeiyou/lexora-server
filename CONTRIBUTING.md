# 参与贡献

欢迎提交问题报告、文档改进和代码贡献。

## 报告问题

请说明复现步骤、预期与实际行为、Java 版本和部署方式。提交日志前移除 API key、JWT、密码和用户数据。安全问题请通过仓库的 Security 页面私下报告。

## 提交改动

1. Fork 仓库，从 `main` 创建工作分支。
2. 保持改动聚焦，行为变化请补充相应测试和文档。
3. 使用 JDK 21 运行 `./mvnw verify`。
4. 涉及发布脚本时，运行 `bash scripts/test-build-release-image.sh` 和 `bash scripts/test-build-release-systemd-package.sh`。
5. 提交 Pull Request，说明改动目的和验证结果。

不要提交真实 `.env`、`config/application.yml`、音频、数据库导出或构建产物。提交贡献表示你同意以项目的 MIT 许可证发布贡献内容；第三方代码需保留原始许可与归属说明。
