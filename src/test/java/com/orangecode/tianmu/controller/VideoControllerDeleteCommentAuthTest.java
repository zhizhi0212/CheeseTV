package com.orangecode.tianmu.controller;

import com.orangecode.tianmu.application.CurrentUser;
import com.orangecode.tianmu.common.ErrorCode;
import com.orangecode.tianmu.exception.BusinessException;
import com.orangecode.tianmu.model.dto.video.CancelVideoActionRequest;
import com.orangecode.tianmu.service.CommentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VideoControllerDeleteCommentAuthTest {
    private VideoController controller;
    private CommentService comments;
    private MockHttpServletRequest httpRequest;

    @BeforeEach
    void setUp() {
        controller = new VideoController();
        comments = mock(CommentService.class);
        httpRequest = new MockHttpServletRequest();
        httpRequest.setAttribute(CurrentUser.ATTRIBUTE, 7L);
        ReflectionTestUtils.setField(controller, "commentService", comments);
        ReflectionTestUtils.setField(controller, "currentUser", new CurrentUser(httpRequest));
    }

    @Test
    void deletionPassesAuthenticatedUserToService() {
        CancelVideoActionRequest request = request();
        when(comments.deleteCommentVideo(request, 7L)).thenReturn(true);

        var response = controller.deleteCommentVideo(request);

        assertEquals(200, response.getCode());
        assertEquals(true, response.getData());
        verify(comments).deleteCommentVideo(request, 7L);
    }

    @Test
    void unauthenticatedDeletionNeverReachesService() {
        httpRequest.removeAttribute(CurrentUser.ATTRIBUTE);

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.deleteCommentVideo(request()));

        assertEquals(ErrorCode.NOT_LOGIN_ERROR.getCode(), error.getCode());
        verifyNoInteractions(comments);
    }

    private CancelVideoActionRequest request() {
        CancelVideoActionRequest request = new CancelVideoActionRequest();
        request.setId(11L);
        request.setVideoId(99L);
        return request;
    }
}
