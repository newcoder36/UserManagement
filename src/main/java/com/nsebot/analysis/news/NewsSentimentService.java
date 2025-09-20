package com.nsebot.analysis.news;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * News Sentiment Analysis Service
 * 
 * Analyzes news articles and social media sentiment for stock symbols to provide
 * sentiment scores that complement technical analysis.
 * 
 * Features:
 * - Web scraping of financial news sources
 * - Sentiment classification (Positive/Negative/Neutral)
 * - Keyword-based sentiment scoring
 * - Volume-weighted sentiment analysis
 */
@Service
public class NewsSentimentService {
    
    private static final Logger logger = LoggerFactory.getLogger(NewsSentimentService.class);
    
    // Sentiment word dictionaries
    private static final Set<String> POSITIVE_WORDS = Set.of(
        "bullish", "buy", "growth", "profit", "gain", "strong", "rise", "up", "positive", "good",
        "excellent", "outstanding", "beat", "exceed", "upgrade", "recommend", "target", "momentum",
        "surge", "rally", "breakthrough", "success", "improvement", "expansion", "optimistic"
    );
    
    private static final Set<String> NEGATIVE_WORDS = Set.of(
        "bearish", "sell", "loss", "decline", "fall", "down", "negative", "bad", "poor",
        "disappointing", "miss", "downgrade", "concern", "warning", "risk", "volatility",
        "drop", "crash", "uncertainty", "problem", "challenge", "weakness", "pessimistic"
    );
    
    private static final Set<String> INTENSIFIERS = Set.of(
        "very", "extremely", "highly", "significantly", "strongly", "dramatically", "substantially"
    );
    
    /**
     * Analyze sentiment for a stock symbol
     */
    // @Cacheable(value = "newsSentiment", key = "#symbol", unless = "#result.confidence < 30") // Disabled for real-time data
    public NewsSentimentResult analyzeSentiment(String symbol) {
        logger.info("Analyzing news sentiment for symbol: {}", symbol);
        
        try {
            // Collect news articles from multiple sources
            List<NewsArticle> articles = collectNewsArticles(symbol);
            
            if (articles.isEmpty()) {
                return new NewsSentimentResult(symbol, SentimentScore.NEUTRAL, new BigDecimal("0"),
                        "No recent news articles found for sentiment analysis");
            }
            
            // Analyze sentiment of collected articles
            SentimentAnalysis overallSentiment = analyzeSentiment(articles);
            
            // Generate interpretation
            String interpretation = generateSentimentInterpretation(symbol, articles.size(), overallSentiment);
            
            return new NewsSentimentResult(symbol, overallSentiment.sentiment, 
                    overallSentiment.confidence, interpretation);
            
        } catch (Exception e) {
            logger.error("Error analyzing sentiment for symbol: {}", symbol, e);
            return new NewsSentimentResult(symbol, SentimentScore.NEUTRAL, new BigDecimal("0"),
                    "Error analyzing news sentiment: " + e.getMessage());
        }
    }
    
    /**
     * Collect news articles from various sources
     */
    private List<NewsArticle> collectNewsArticles(String symbol) {
        List<NewsArticle> articles = new ArrayList<>();
        
        try {
            // Simulate news collection from multiple sources
            // In production, this would integrate with actual news APIs
            articles.addAll(getEconomicTimesNews(symbol));
            articles.addAll(getMoneyControlNews(symbol));
            articles.addAll(getBusinessStandardNews(symbol));
            
        } catch (Exception e) {
            logger.warn("Error collecting news articles for symbol: {}", symbol, e);
        }
        
        return articles;
    }
    
    /**
     * Simulate Economic Times news collection
     */
    private List<NewsArticle> getEconomicTimesNews(String symbol) {
        // Mock implementation - in production, integrate with actual ET API
        List<NewsArticle> articles = new ArrayList<>();
        
        // Generate sample articles based on symbol for demonstration
        String[] sampleTitles = {
            symbol + " shows strong quarterly results, analysts bullish on growth prospects",
            "Market experts recommend " + symbol + " as top pick for upcoming quarter",
            symbol + " stock hits new high amid positive industry outlook",
            "Concerns over " + symbol + " debt levels, analysts suggest caution",
            symbol + " management optimistic about future growth strategies"
        };
        
        for (int i = 0; i < Math.min(3, sampleTitles.length); i++) {
            articles.add(new NewsArticle(
                sampleTitles[i],
                "Economic Times",
                LocalDateTime.now().minusHours(i + 1),
                generateSampleContent(sampleTitles[i])
            ));
        }
        
        return articles;
    }
    
    /**
     * Simulate MoneyControl news collection
     */
    private List<NewsArticle> getMoneyControlNews(String symbol) {
        // Mock implementation
        List<NewsArticle> articles = new ArrayList<>();
        
        String[] sampleTitles = {
            symbol + " Q3 earnings beat estimates, stock surges in morning trade",
            "Technical analysis: " + symbol + " breaks key resistance levels",
            "Why " + symbol + " could be the dark horse of this sector"
        };
        
        for (int i = 0; i < Math.min(2, sampleTitles.length); i++) {
            articles.add(new NewsArticle(
                sampleTitles[i],
                "MoneyControl",
                LocalDateTime.now().minusHours(i + 3),
                generateSampleContent(sampleTitles[i])
            ));
        }
        
        return articles;
    }
    
    /**
     * Simulate Business Standard news collection
     */
    private List<NewsArticle> getBusinessStandardNews(String symbol) {
        // Mock implementation
        List<NewsArticle> articles = new ArrayList<>();
        
        articles.add(new NewsArticle(
            symbol + " announces major expansion plans, stock rallies",
            "Business Standard",
            LocalDateTime.now().minusHours(2),
            generateSampleContent(symbol + " expansion plans positive outlook")
        ));
        
        return articles;
    }
    
    /**
     * Generate sample article content for demonstration
     */
    private String generateSampleContent(String title) {
        // Simple content generation based on title sentiment
        String[] positiveSnippets = {
            "The company reported strong financial results with revenue growth exceeding expectations.",
            "Market analysts have upgraded their price targets following positive developments.",
            "Management expressed confidence in the company's strategic direction and growth prospects."
        };
        
        String[] negativeSnippets = {
            "The company faces headwinds due to challenging market conditions.",
            "Analysts have raised concerns about the company's debt levels and market position.",
            "Recent developments have led to uncertainty among investors."
        };
        
        // Simple sentiment detection in title
        boolean isPositive = POSITIVE_WORDS.stream().anyMatch(word -> 
            title.toLowerCase().contains(word));
        boolean isNegative = NEGATIVE_WORDS.stream().anyMatch(word -> 
            title.toLowerCase().contains(word));
        
        if (isPositive && !isNegative) {
            return String.join(" ", Arrays.copyOf(positiveSnippets, 2));
        } else if (isNegative && !isPositive) {
            return String.join(" ", Arrays.copyOf(negativeSnippets, 2));
        } else {
            return "The company continues to navigate market conditions while focusing on operational efficiency.";
        }
    }
    
    /**
     * Analyze sentiment of collected articles
     */
    private SentimentAnalysis analyzeSentiment(List<NewsArticle> articles) {
        if (articles.isEmpty()) {
            return new SentimentAnalysis(SentimentScore.NEUTRAL, new BigDecimal("0"));
        }
        
        BigDecimal totalSentimentScore = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        
        for (NewsArticle article : articles) {
            BigDecimal articleSentiment = calculateArticleSentiment(article);
            BigDecimal weight = calculateArticleWeight(article);
            
            totalSentimentScore = totalSentimentScore.add(articleSentiment.multiply(weight));
            totalWeight = totalWeight.add(weight);
        }
        
        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
            return new SentimentAnalysis(SentimentScore.NEUTRAL, new BigDecimal("0"));
        }
        
        BigDecimal averageSentiment = totalSentimentScore.divide(totalWeight, 4, RoundingMode.HALF_UP);
        
        // Convert to sentiment score and confidence
        SentimentScore sentiment = convertToSentimentScore(averageSentiment);
        BigDecimal confidence = calculateConfidence(articles.size(), averageSentiment);
        
        return new SentimentAnalysis(sentiment, confidence);
    }
    
    /**
     * Calculate sentiment score for individual article
     */
    private BigDecimal calculateArticleSentiment(NewsArticle article) {
        String text = (article.getTitle() + " " + article.getContent()).toLowerCase();
        String[] words = text.split("\\s+");
        
        int positiveCount = 0;
        int negativeCount = 0;
        int intensifierMultiplier = 1;
        
        for (int i = 0; i < words.length; i++) {
            String word = words[i].replaceAll("[^a-zA-Z]", "");
            
            // Check for intensifiers
            if (INTENSIFIERS.contains(word) && i < words.length - 1) {
                intensifierMultiplier = 2;
                continue;
            }
            
            if (POSITIVE_WORDS.contains(word)) {
                positiveCount += intensifierMultiplier;
            } else if (NEGATIVE_WORDS.contains(word)) {
                negativeCount += intensifierMultiplier;
            }
            
            intensifierMultiplier = 1; // Reset after each word
        }
        
        int totalSentimentWords = positiveCount + negativeCount;
        if (totalSentimentWords == 0) {
            return BigDecimal.ZERO; // Neutral
        }
        
        // Calculate sentiment score (-1 to +1)
        BigDecimal sentimentScore = new BigDecimal(positiveCount - negativeCount)
                .divide(new BigDecimal(totalSentimentWords), 4, RoundingMode.HALF_UP);
        
        return sentimentScore;
    }
    
    /**
     * Calculate weight of article based on recency and source credibility
     */
    private BigDecimal calculateArticleWeight(NewsArticle article) {
        BigDecimal baseWeight = new BigDecimal("1.0");
        
        // Weight by recency (more recent = higher weight)
        LocalDateTime now = LocalDateTime.now();
        long hoursAgo = java.time.Duration.between(article.getTimestamp(), now).toHours();
        
        if (hoursAgo <= 6) {
            baseWeight = baseWeight.multiply(new BigDecimal("1.5"));
        } else if (hoursAgo <= 24) {
            baseWeight = baseWeight.multiply(new BigDecimal("1.2"));
        } else if (hoursAgo <= 72) {
            baseWeight = baseWeight.multiply(new BigDecimal("1.0"));
        } else {
            baseWeight = baseWeight.multiply(new BigDecimal("0.7"));
        }
        
        // Weight by source credibility
        switch (article.getSource().toLowerCase()) {
            case "economic times", "business standard", "moneycontrol" -> baseWeight = baseWeight.multiply(new BigDecimal("1.2"));
            case "reuters", "bloomberg" -> baseWeight = baseWeight.multiply(new BigDecimal("1.3"));
            default -> baseWeight = baseWeight.multiply(new BigDecimal("1.0"));
        }
        
        return baseWeight;
    }
    
    /**
     * Convert numeric sentiment to categorical score
     */
    private SentimentScore convertToSentimentScore(BigDecimal sentiment) {
        if (sentiment.compareTo(new BigDecimal("0.3")) > 0) {
            return SentimentScore.POSITIVE;
        } else if (sentiment.compareTo(new BigDecimal("-0.3")) < 0) {
            return SentimentScore.NEGATIVE;
        } else {
            return SentimentScore.NEUTRAL;
        }
    }
    
    /**
     * Calculate confidence based on article count and sentiment consistency
     */
    private BigDecimal calculateConfidence(int articleCount, BigDecimal averageSentiment) {
        // Base confidence on number of articles
        BigDecimal baseConfidence = new BigDecimal(Math.min(articleCount * 15, 70));
        
        // Adjust based on sentiment strength
        BigDecimal sentimentStrength = averageSentiment.abs().multiply(new BigDecimal("30"));
        
        BigDecimal totalConfidence = baseConfidence.add(sentimentStrength);
        
        // Cap at 90%
        return totalConfidence.min(new BigDecimal("90"));
    }
    
    /**
     * Generate human-readable interpretation
     */
    private String generateSentimentInterpretation(String symbol, int articleCount, SentimentAnalysis analysis) {
        String sentimentText = switch (analysis.sentiment) {
            case POSITIVE -> "Positive";
            case NEGATIVE -> "Negative";
            case NEUTRAL -> "Neutral";
        };
        
        return String.format("News Sentiment: %s (%.0f%% confidence) - Analyzed %d recent articles for %s", 
                sentimentText, analysis.confidence, articleCount, symbol);
    }
    
    /**
     * News Article class
     */
    private static class NewsArticle {
        private final String title;
        private final String source;
        private final LocalDateTime timestamp;
        private final String content;
        
        public NewsArticle(String title, String source, LocalDateTime timestamp, String content) {
            this.title = title;
            this.source = source;
            this.timestamp = timestamp;
            this.content = content;
        }
        
        public String getTitle() { return title; }
        public String getSource() { return source; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getContent() { return content; }
    }
    
    /**
     * Sentiment Analysis result
     */
    private static class SentimentAnalysis {
        private final SentimentScore sentiment;
        private final BigDecimal confidence;
        
        public SentimentAnalysis(SentimentScore sentiment, BigDecimal confidence) {
            this.sentiment = sentiment;
            this.confidence = confidence;
        }
    }
    
    /**
     * News Sentiment Result
     */
    public static class NewsSentimentResult {
        private final String symbol;
        private final SentimentScore sentiment;
        private final BigDecimal confidence;
        private final String interpretation;
        
        public NewsSentimentResult(String symbol, SentimentScore sentiment, 
                                 BigDecimal confidence, String interpretation) {
            this.symbol = symbol;
            this.sentiment = sentiment;
            this.confidence = confidence;
            this.interpretation = interpretation;
        }
        
        public String getSymbol() { return symbol; }
        public SentimentScore getSentiment() { return sentiment; }
        public BigDecimal getConfidence() { return confidence; }
        public String getInterpretation() { return interpretation; }
        
        public boolean isBullish() { return sentiment == SentimentScore.POSITIVE; }
        public boolean isBearish() { return sentiment == SentimentScore.NEGATIVE; }
        public boolean isNeutral() { return sentiment == SentimentScore.NEUTRAL; }
        
        @Override
        public String toString() {
            return String.format("NewsSentiment(%s, %s, %.0f%%)", symbol, sentiment, confidence);
        }
    }
    
    /**
     * Sentiment Score enumeration
     */
    public enum SentimentScore {
        POSITIVE,  // Bullish news sentiment
        NEGATIVE,  // Bearish news sentiment
        NEUTRAL    // Mixed or no clear sentiment
    }
}