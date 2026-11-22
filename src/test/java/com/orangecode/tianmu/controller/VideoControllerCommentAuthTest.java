package com.orangecode.tianmu.controller;

import com.orangecode.tianmu.application.CurrentUser;
import com.orangecode.tianmu.common.BaseResponse;
import com.orangecode.tianmu.common.ErrorCode;
import com.orangecode.tianmu.exception.BusinessException;
import com.orangecode.tianmu.model.dto.video.CreateCommentRequest;
import com.orangecode.tianmu.model.vo.video.CommentResponse;
import com.orangecode.tianmu.service.CommentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VideoControllerCommentAuthTest {
    private VideoController controller;
    private CommentService commentService;
    private MockHttpServletRequest httpRequest;

    @BeforeEach
    void setUp() {
        controller = new VideoController();
        commentService = mock(CommentService.class);
        httpRequest = new MockHttpServletRequest();
        httpRequest.setAttribute(CurrentUser.ATTRIBUTE, 7L);
        ReflectionTestUtils.setField(controller, "commentService", commentService);
        ReflectionTestUtils.setField(controller, "currentUser", new CurrentUser(httpRequest));
    }

    @Test
    void matchingUserCreatesComment() {
        CreateCommentRequest request = commentRequest(7L);
        CommentResponse comment = new CommentResponse();
        when(commentService.createCommentVideo(request)).thenReturn(comment);

        BaseResponse<CommentResponse> result = controller.createCommentVideo(request);

        assertEquals(200, result.getCode());
        assertSame(comment, result.getData());
        verify(commentService).createCommentVideo(request);
    }

    @Test
    void forgedUserIsRejectedBeforeCommentService() {
        CreateCommentRequest request = commentRequest(8L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.createCommentVideo(request));

        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), error.getCode());
        verifyNoInteractions(commentService);
    }

    @Test
    void missingRequestUserIdUsesAuthenticatedUser() {
        CreateCommentRequest request = commentRequest(null);

        controller.createCommentVideo(request);

        assertEquals(Long.valueOf(7L), request.getUserId());
        verify(commentService).createCommentVideo(request);
    }

    @Test
    void missingAuthenticatedUserIsRejectedBeforeCommentService() {
        httpRequest.removeAttribute(CurrentUser.ATTRIBUTE);
        CreateCommentRequest request = commentRequest(7L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.createCommentVideo(request));

        assertEquals(ErrorCode.NOT_LOGIN_ERROR.getCode(), error.getCode());
        verifyNoInteractions(commentService);
    }

    private CreateCommentRequest commentRequest(Long userId) {
        CreateCommentRequest request = new CreateCommentRequest();
        request.setContent("test comment");
        request.setVideoId(99L);
        request.setUserId(userId);
        return request;
    }
}
