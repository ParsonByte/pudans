# DeepSeek4j migration

The DeepSeek protocol models in this directory are derived from
[`pig-mesh/deepseek4j`](https://github.com/pig-mesh/deepseek4j), version `1.6.1`,
commit `e8ff46bad0689b62f6e80d174d248d59ca128f7d`.

The fork keeps the documented Models catalog wire fields, reuses the project's
shared Search and FIM contract DTOs, removes the Retrofit/OkHttp transport and
builder boilerplate, and uses the single reactive `AiProviderHttp` gateway and
shared Jackson mapper instead.

The upstream project is licensed under Apache License 2.0. The license text is
available at `third-party/deepseek4j/LICENSE`.
