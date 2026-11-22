package com.orangecode.tianmu.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.orangecode.tianmu.application.CurrentUser;
import com.orangecode.tianmu.common.ErrorCode;
import com.orangecode.tianmu.exception.BusinessException;
import com.orangecode.tianmu.model.dto.video.VideoSubmitRequest;
import com.orangecode.tianmu.model.entity.Video;
import com.orangecode.tianmu.model.entity.VideoStats;
import com.orangecode.tianmu.service.CategoryService;
import com.orangecode.tianmu.service.FileService;
import com.orangecode.tianmu.service.UserService;
import com.orangecode.tianmu.service.UserStatsService;
import com.orangecode.tianmu.service.VideoStatsService;
import com.orangecode.tianmu.utils.MinioUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VideoSubmitAuthTest {
    private VideoServiceImpl service;
    private MockHttpServletRequest httpRequest;
    private MinioUtil minio;
    private FileService files;
    private UserService users;
    private CategoryService categories;
    private VideoStatsService videoStats;
    private UserStatsService userStats;

    @BeforeEach
    void setUp() {
        service = spy(new VideoServiceImpl());
        httpRequest = new MockHttpServletRequest();
        httpRequest.setAttribute(CurrentUser.ATTRIBUTE, 7L);
        minio = mock(MinioUtil.class);
        files = mock(FileService.class);
        users = mock(UserService.class);
        categories = mock(CategoryService.class);
        videoStats = mock(VideoStatsService.class);
        userStats = mock(UserStatsService.class);
        ReflectionTestUtils.setField(service, "currentUser", new CurrentUser(httpRequest));
        ReflectionTestUtils.setField(service, "minioUtil", minio);
        ReflectionTestUtils.setField(service, "fileService", files);
        ReflectionTestUtils.setField(service, "userService", users);
        ReflectionTestUtils.setField(service, "categoryService", categories);
        ReflectionTestUtils.setField(service, "videoStatsService", videoStats);
        ReflectionTestUtils.setField(service, "userStatsService", userStats);
    }

    @Test
    void forgedAuthorIsRejectedBeforeCoverUploadOrDatabaseWork() {
        assertCode(ErrorCode.NO_AUTH_ERROR, () -> service.submit(request(8L)));

        verifyNoInteractions(minio, files, users, categories, videoStats, userStats);
    }

    @Test
    void missingAuthenticatedUserIsRejectedBeforeCoverUploadOrDatabaseWork() {
        httpRequest.removeAttribute(CurrentUser.ATTRIBUTE);

        assertCode(ErrorCode.NOT_LOGIN_ERROR, () -> service.submit(request(7L)));

        verifyNoInteractions(minio, files, users, categories, videoStats, userStats);
    }

    @Test
    void matchingUserBecomesVideoAuthorAndReceivesSubmissionCount() throws Exception {
        LambdaQueryChainWrapper fileQuery = existingQuery();
        LambdaQueryChainWrapper userQuery = existingQuery();
        LambdaQueryChainWrapper categoryQuery = existingQuery();
        when(files.lambdaQuery()).thenReturn(fileQuery);
        when(users.lambdaQuery()).thenReturn(userQuery);
        when(categories.lambdaQuery()).thenReturn(categoryQuery);
        when(minio.updateCover(any())).thenReturn("https://example.test/cover.png");
        doReturn(true).when(service).save(any(Video.class));
        when(videoStats.save(any(VideoStats.class))).thenReturn(true);

        LambdaUpdateChainWrapper update = mock(LambdaUpdateChainWrapper.class, RETURNS_SELF);
        doReturn(update).when(update).setSql(anyString(), any(Object[].class));
        doReturn(update).when(update).eq(any(), any());
        when(update.update()).thenReturn(true);
        when(userStats.lambdaUpdate()).thenReturn(update);

        assertTrue(service.submit(request(7L)));

        verify(service).save(argThat((Video video) -> video.getUserId() == 7L));
        verify(update).eq(any(), eq(7L));
        verify(update).update();
    }

    private LambdaQueryChainWrapper existingQuery() {
        LambdaQueryChainWrapper query = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        doReturn(query).when(query).eq(any(), any());
        when(query.exists()).thenReturn(true);
        return query;
    }

    private VideoSubmitRequest request(Long userId) {
        return new VideoSubmitRequest(
                "https://example.test/video.mp4",
                userId,
                new MockMultipartFile("file", "cover.png", "image/png", new byte[]{1}),
                "test video", 1, 12.0, 3, "java", "description");
    }

    private void assertCode(ErrorCode code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code.getCode(), assertThrows(BusinessException.class, action).getCode());
    }
}
