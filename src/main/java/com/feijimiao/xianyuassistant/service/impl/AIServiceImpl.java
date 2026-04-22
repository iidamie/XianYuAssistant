package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.service.AIService;
import com.feijimiao.xianyuassistant.service.SysSettingService;
import com.feijimiao.xianyuassistant.service.bo.RAGDataRespBO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * @author IAMLZY
 * @date 2026/4/10 22:26
 * @description AI服务实现，仅在ai.enabled=true时加载
 */

@Service
@Slf4j
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
public class AIServiceImpl implements AIService {

    /** 系统提示词配置键 */
    private static final String SYS_PROMPT_KEY = "sys_prompt";

    /** 默认系统提示词 */
    private static final String DEFAULT_SYS_PROMPT = "你是一个闲鱼卖家，你叫肥极喵，不要回复的像AI，简短回答\n参考相关信息回答,不要乱回答,不知道就换不同语气回复提示用户详细点询问";

    @Autowired
    @Qualifier("XianYuChatClient")
    ChatClient chatClient;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private SysSettingService sysSettingService;

    @Override
    public Flux<String> chatByRAG(String prompt,String goodsId) {
        long startTime = System.currentTimeMillis();
        log.info("[AI Chat] 收到请求, prompt={}, goodsId={}", prompt, goodsId);

        // 1. 先从向量库搜索相关内容
        long searchStart = System.currentTimeMillis();
        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(prompt)
                        .topK(5)
                        .similarityThreshold(0.1)
                        .filterExpression(String.format("goodsId == '%s'",goodsId))
                        .build()
        );
        long searchCost = System.currentTimeMillis() - searchStart;
        log.info("[AI Chat] 向量搜索耗时: {}ms, 命中文档数: {}", searchCost, documents.size());

        // 2. 把搜索结果拼成上下文
        String context = documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));

        // 3. 从系统配置中获取系统提示词
        String sysPrompt = sysSettingService.getSettingValue(SYS_PROMPT_KEY);
        if (sysPrompt == null || sysPrompt.trim().isEmpty()) {
            sysPrompt = DEFAULT_SYS_PROMPT;
            log.info("[AI Chat] 使用默认系统提示词");
        } else {
            log.info("[AI Chat] 使用自定义系统提示词");
        }

        // 4. 构建用户消息（参考资料 + 用户问题）
        String userMessage = String.format("""
                参考资料：
                %s

                用户问题：%s
                """, context, prompt);

        long llmStart = System.currentTimeMillis();
        log.info("[AI Chat] 准备调用LLM, 总预处理耗时: {}ms", llmStart - startTime);
        log.info("[AI Chat] LLM请求参数 - model: deepseek-v3, temperature: 0.7, goodsId: {}", goodsId);
        log.info("[AI Chat] 系统提示词:\n{}", sysPrompt);
        log.info("[AI Chat] 用户消息:\n{}", userMessage);

        // 5. 请求大模型，流式返回
        AtomicBoolean firstTokenLogged = new AtomicBoolean(false);
        return chatClient.prompt()
                .system(sysPrompt)
                .user(userMessage)
                .stream()
                .content()
                .doOnNext(token -> {
                    if (firstTokenLogged.compareAndSet(false, true)) {
                        long firstTokenCost = System.currentTimeMillis() - llmStart;
                        log.info("[AI Chat] 首 token 耗时: {}ms (从请求到LLM开始输出)", firstTokenCost);
                    }
                });
    }

    @Override
    public void putDataToRAG(String content, String goodsId) {
        log.info("[AI RAG] 写入Chroma, goodsId={}, 内容长度={}字符, 内容={}", goodsId, content.length(), content);

        Map<String,Object> metadata = new HashMap<>();
        metadata.put("goodsId",goodsId);
        metadata.put("createTime",new Date());

        Document document = new Document(content,metadata);

        // 自动切片，默认配置参数：
        //         800,    // defaultChunkSize     每个 chunk 的 token 数
        //        350,    // minChunkSizeChars    最小 chunk 字符数
        //        5,      // minChunkLengthToEmbed 低于此 token 数的 chunk 会被丢弃
        //        10000,  // maxNumChunks         最大 chunk 数量
        //        true    // keepSeparator        是否保留分隔符
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> chunks = splitter.apply(List.of(document));
        log.info("[AI RAG] 切片完成, 切片数={}, 写入Chroma...", chunks.size());
        vectorStore.add(chunks);
        log.info("[AI RAG] 写入Chroma完成");
    }

    @Override
    public List<RAGDataRespBO> queryRAGDataBygoodsId(String goodsId) {
        log.info("[AI RAG] 查询Chroma数据, goodsId={}", goodsId);

        // 用宽泛查询 + 低阈值 + goodsId过滤，获取该商品的所有向量数据
        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("")
                        .topK(100)
                        .similarityThreshold(0.0)
                        .filterExpression(String.format("goodsId == '%s'", goodsId))
                        .build()
        );

        List<RAGDataRespBO> result = documents.stream().map(doc -> {
            RAGDataRespBO bo = new RAGDataRespBO();
            bo.setDocumentId(doc.getId());
            bo.setGoodsID(goodsId);
            bo.setContent(doc.getText());
            Object createTime = doc.getMetadata().get("createTime");
            bo.setCreateTime(createTime != null ? createTime.toString() : null);
            return bo;
        }).collect(Collectors.toList());

        log.info("[AI RAG] 查询Chroma数据完成, goodsId={}, 结果数={}", goodsId, result.size());
        return result;
    }

    @Override
    public void deleteRAGDataByDocumentId(String documentId) {
        log.info("[AI RAG] 删除Chroma数据, documentId={}", documentId);
        vectorStore.delete(List.of(documentId));
        log.info("[AI RAG] 删除Chroma数据完成, documentId={}", documentId);
    }
}
