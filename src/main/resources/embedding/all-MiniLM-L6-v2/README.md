# all-MiniLM-L6-v2 模型说明

这个目录保存 Spring AI Transformers 使用的 ONNX Embedding 模型和分词器。
模型文件放在项目的 classpath 中，因此应用启动时不需要再从网络下载模型。

第一次导入项目时，Maven 仍然需要下载 Java 依赖。第一次启动应用时，DJL 也可能
下载当前操作系统对应的 CPU 原生运行库。这属于运行环境初始化，并不是再次下载
Embedding 模型；后续启动会直接使用本地缓存。

- 模型：`sentence-transformers/all-MiniLM-L6-v2`
- 来源：`https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2`
- 许可证：Apache-2.0
- 输出维度：384
- 模型 SHA-256：`6fd5d72fe4589f189f8ebc006442dbb529bb7ce38f8082112682524616046452`
- 分词器 SHA-256：`da0e79933b9ed51798a3ae27893d3c5fa4a201126cef75586296df9b4d2c62a0`

这个默认模型体积较小、运行较快，但主要面向英文文本。替换模型时，需要同时替换
`model.onnx` 和 `tokenizer.json`，并确认配置项
`spring.ai.embedding.transformer.onnx.model-output-name` 与新模型的输出节点名称一致。
