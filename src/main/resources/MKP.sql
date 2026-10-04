-- MySQL dump 10.13  Distrib 8.0.41, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: mkp_db
-- ------------------------------------------------------
-- Server version	8.0.41

--
-- 说明：原始数据（INSERT）段已移除，仅保留建表结构，便于公开分享。
--

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `mkp_chatbot_knowledge`
--

DROP TABLE IF EXISTS `mkp_chatbot_knowledge`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mkp_chatbot_knowledge` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `keyword` varchar(200) NOT NULL COMMENT 'Match keyword(s), comma-separated',
  `answer` text NOT NULL COMMENT 'Answer content',
  `category` varchar(50) NOT NULL DEFAULT '' COMMENT 'Category',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT 'Logical delete',
  PRIMARY KEY (`id`),
  KEY `idx_keyword` (`keyword`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Chatbot knowledge base';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mkp_comment`
--

DROP TABLE IF EXISTS `mkp_comment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mkp_comment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `post_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `content` text NOT NULL,
  `parent_id` bigint DEFAULT NULL COMMENT '父评论ID，为空表示顶级评论',
  `like_count` int DEFAULT '0',
  `deleted` tinyint DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_post_id` (`post_id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mkp_comment_like`
--

DROP TABLE IF EXISTS `mkp_comment_like`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mkp_comment_like` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `comment_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comment_user` (`comment_id`,`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mkp_friend_relation`
--

DROP TABLE IF EXISTS `mkp_friend_relation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mkp_friend_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `user_id` bigint NOT NULL COMMENT 'Initiator user ID (smaller ID)',
  `friend_id` bigint NOT NULL COMMENT 'Target user ID (larger ID)',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0=pending, 1=accepted, 2=rejected',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
  `sender_id` bigint DEFAULT NULL COMMENT '发起请求的用户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_friend` (`user_id`,`friend_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_friend_id` (`friend_id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Friend relation table';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mkp_like`
--

DROP TABLE IF EXISTS `mkp_like`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mkp_like` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `post_id` bigint NOT NULL COMMENT 'Post ID',
  `user_id` bigint NOT NULL COMMENT 'User who liked',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_user` (`post_id`,`user_id`),
  KEY `idx_post_id` (`post_id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Post like record';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mkp_message`
--

DROP TABLE IF EXISTS `mkp_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mkp_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Message ID',
  `from_user_id` bigint NOT NULL COMMENT 'Sender user ID',
  `to_user_id` bigint NOT NULL COMMENT 'Receiver user ID',
  `content` text NOT NULL COMMENT 'Message content',
  `message_type` tinyint NOT NULL DEFAULT '0' COMMENT '0=text, 1=image',
  `is_read` tinyint NOT NULL DEFAULT '0' COMMENT '0=unread, 1=read',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Sent at',
  PRIMARY KEY (`id`),
  KEY `idx_from_to` (`from_user_id`,`to_user_id`),
  KEY `idx_to_read` (`to_user_id`,`is_read`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Chat message table';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mkp_post`
--

DROP TABLE IF EXISTS `mkp_post`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mkp_post` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Post ID',
  `user_id` bigint NOT NULL COMMENT 'Author user ID',
  `content` text NOT NULL COMMENT 'Post text content',
  `images` varchar(2000) NOT NULL DEFAULT '' COMMENT 'Comma-separated image URLs',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '0=draft, 1=published, 2=deleted',
  `view_count` int NOT NULL DEFAULT '0' COMMENT 'View count',
  `like_count` int NOT NULL DEFAULT '0' COMMENT 'Like count',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT 'Logical delete',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Post table';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mkp_user`
--

DROP TABLE IF EXISTS `mkp_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mkp_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'User ID',
  `username` varchar(50) NOT NULL COMMENT 'Login username',
  `password` varchar(255) NOT NULL COMMENT 'BCrypt hashed password',
  `nickname` varchar(50) NOT NULL DEFAULT '' COMMENT 'Display nickname',
  `avatar` varchar(500) NOT NULL DEFAULT '' COMMENT 'Avatar image URL',
  `phone` varchar(20) NOT NULL DEFAULT '' COMMENT 'Phone number',
  `role` varchar(20) NOT NULL DEFAULT 'USER' COMMENT 'Role: ADMIN or USER',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT 'Status: 0=disabled, 1=enabled',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT 'Logical delete: 0=no, 1=yes',
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`),
  KEY `idx_username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='User table';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-12  9:33:13
