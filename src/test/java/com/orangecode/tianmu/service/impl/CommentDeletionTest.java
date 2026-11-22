package com.orangecode.tianmu.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.orangecode.tianmu.common.ErrorCode;
import com.orangecode.tianmu.exception.BusinessException;
import com.orangecode.tianmu.mapper.CommentMapper;
import com.orangecode.tianmu.model.dto.video.CancelVideoActionRequest;
import com.orangecode.tianmu.model.entity.Comment;
import com.orangecode.tianmu.model.entity.VideoStats;
import com.orangecode.tianmu.service.VideoStatsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CommentDeletionTest {
    private CommentServiceImpl service;
    private CommentMapper mapper;
    private VideoStatsService stats;
    private LambdaQueryChainWrapper<VideoStats> statsQuery;
    private LambdaUpdateChainWrapper<VideoStats> update;

    @BeforeEach
    void setUp() {
        service = spy(new CommentServiceImpl());
        mapper = mock(CommentMapper.class);
        stats = mock(VideoStatsService.class);
        statsQuery = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        update = mock(LambdaUpdateChainWrapper.class, RETURNS_SELF);
        ReflectionTestUtils.setField(service, "videoStatsService", stats);
        doReturn(mapper).when(service).getBaseMapper();

        when(stats.lambdaQuery()).thenReturn(statsQuery);
        doReturn(statsQuery).when(statsQuery).eq(any(), any());
        doReturn(statsQuery).when(statsQuery).last(anyString());
        when(statsQuery.one()).thenReturn(new VideoStats());
        doReturn(update).when(update).setSql(anyString(), any(Object[].class));
        doReturn(update).when(update).eq(any(), any());
        doReturn(update).when(update).ge(any(), any());
        when(stats.lambdaUpdate()).thenReturn(update);
        when(update.update()).thenReturn(true);
    }

    @Test
    void ownerDeletesRootAndEveryNestedReplyButNotSiblingRoot() {
        storedComment(11L, 0L, 7L, 99L);
        videoComments(List.of(comment(11L, 0L, 7L, 99L), comment(12L, 11L, 8L, 99L),
                comment(13L, 12L, 9L, 99L), comment(14L, 0L, 10L, 99L)));
        when(mapper.delete(any(Wrapper.class))).thenReturn(3);

        assertTrue(service.deleteCommentVideo(request(11L, 99L), 7L));

        assertDeletedIds(List.of(11L, 12L, 13L), List.of(14L));
        assertScopedChildQueries();
        assertCountUpdate(3, 99L);
        verify(statsQuery).last("FOR UPDATE");
    }

    @Test
    void deletingReplyRemovesOnlyItsOwnSubtree() {
        storedComment(12L, 11L, 7L, 99L);
        videoComments(List.of(comment(11L, 0L, 8L, 99L), comment(12L, 11L, 7L, 99L),
                comment(13L, 12L, 9L, 99L), comment(14L, 11L, 10L, 99L),
                comment(15L, 13L, 11L, 99L)));
        when(mapper.delete(any(Wrapper.class))).thenReturn(3);

        assertTrue(service.deleteCommentVideo(request(12L, 99L), 7L));

        assertDeletedIds(List.of(12L, 13L, 15L), List.of(11L, 14L));
        assertCountUpdate(3, 99L);
    }

    @Test
    void legacyCrossVideoReplyIsOutsideDeletionScope() {
        storedComment(11L, 0L, 7L, 99L);
        videoComments(List.of(comment(11L, 0L, 7L, 99L), comment(12L, 11L, 8L, 100L),
                comment(13L, 12L, 9L, 99L)));
        when(mapper.delete(any(Wrapper.class))).thenReturn(1);

        assertTrue(service.deleteCommentVideo(request(11L, 99L), 7L));

        assertDeletedIds(List.of(11L), List.of(12L, 13L));
        assertCountUpdate(1, 99L);
    }

    @Test
    void anotherUserCannotDeleteOrChangeCount() {
        storedComment(11L, 0L, 8L, 99L);

        assertCode(ErrorCode.NO_AUTH_ERROR, () -> service.deleteCommentVideo(request(11L, 99L), 7L));

        verify(service, never()).list(any(Wrapper.class));
        verifyNoInteractions(mapper);
        verify(stats, never()).lambdaUpdate();
    }

    @Test
    void wrongVideoIdCannotChangeAnotherVideosCount() {
        storedComment(11L, 0L, 7L, 99L);

        assertCode(ErrorCode.PARAMS_ERROR, () -> service.deleteCommentVideo(request(11L, 100L), 7L));

        verify(service, never()).list(any(Wrapper.class));
        verifyNoInteractions(mapper);
        verify(stats, never()).lambdaUpdate();
    }

    @Test
    void missingOrAlreadyDeletedCommentDoesNotChangeCount() {
        doReturn(null).when(service).getById(11L);

        assertCode(ErrorCode.DELETE_COMMENT_ERROR, () -> service.deleteCommentVideo(request(11L, 99L), 7L));

        verify(service, never()).list(any(Wrapper.class));
        verifyNoInteractions(mapper);
        verify(stats, never()).lambdaUpdate();
    }

    @Test
    void partialDeleteRaisesErrorBeforeChangingCount() {
        storedComment(11L, 0L, 7L, 99L);
        videoComments(List.of(comment(11L, 0L, 7L, 99L), comment(12L, 11L, 8L, 99L)));
        when(mapper.delete(any(Wrapper.class))).thenReturn(1);

        assertCode(ErrorCode.DELETE_COMMENT_ERROR, () -> service.deleteCommentVideo(request(11L, 99L), 7L));

        verify(stats, never()).lambdaUpdate();
    }

    @Test
    void lostConcurrentDeleteDoesNotDecrementCount() {
        storedComment(11L, 0L, 7L, 99L);
        videoComments(List.of(comment(11L, 0L, 7L, 99L)));
        when(mapper.delete(any(Wrapper.class))).thenReturn(0);

        assertCode(ErrorCode.DELETE_COMMENT_ERROR, () -> service.deleteCommentVideo(request(11L, 99L), 7L));

        verify(stats, never()).lambdaUpdate();
    }

    @Test
    void failedCountUpdateRaisesErrorForTransactionRollback() {
        storedComment(11L, 0L, 7L, 99L);
        videoComments(List.of(comment(11L, 0L, 7L, 99L)));
        when(mapper.delete(any(Wrapper.class))).thenReturn(1);
        when(update.update()).thenReturn(false);

        assertCode(ErrorCode.SYSTEM_ERROR, () -> service.deleteCommentVideo(request(11L, 99L), 7L));
    }

    @Test
    void largeSubtreeUsesBoundedDeleteBatches() {
        storedComment(11L, 0L, 7L, 99L);
        List<Comment> rows = new ArrayList<>();
        rows.add(comment(11L, 0L, 7L, 99L));
        for (long id = 12L; id <= 511L; id++) {
            rows.add(comment(id, 11L, 8L, 99L));
        }
        videoComments(rows);
        when(mapper.delete(any(Wrapper.class))).thenReturn(500, 1);

        assertTrue(service.deleteCommentVideo(request(11L, 99L), 7L));

        verify(mapper, org.mockito.Mockito.times(2)).delete(any(Wrapper.class));
        assertCountUpdate(501, 99L);
    }

    private void storedComment(long id, long parentId, long author, long videoId) {
        doReturn(comment(id, parentId, author, videoId)).when(service).getById(id);
    }

    private void videoComments(List<Comment> comments) {
        doReturn(comments).when(service).list(any(Wrapper.class));
    }

    private Comment comment(long id, long parentId, long author, long videoId) {
        Comment comment = new Comment();
        comment.setCommentId(id);
        comment.setParentCommentId(parentId);
        comment.setUserId(author);
        comment.setVideoId(videoId);
        return comment;
    }

    private void assertDeletedIds(List<Long> expected, List<Long> absent) {
        ArgumentCaptor<Wrapper> deletion = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).delete(deletion.capture());
        QueryWrapper<?> condition = (QueryWrapper<?>) deletion.getValue();
        Collection<?> values = condition.getParamNameValuePairs().values();
        assertTrue(condition.getSqlSegment().contains("video_id"));
        assertTrue(condition.getSqlSegment().contains("comment_id"));
        assertTrue(values.contains(99L));
        assertTrue(values.containsAll(expected));
        for (Long id : absent) {
            assertFalse(values.contains(id));
        }
    }

    private void assertScopedChildQueries() {
        ArgumentCaptor<Wrapper> queries = ArgumentCaptor.forClass(Wrapper.class);
        verify(service, org.mockito.Mockito.atLeastOnce()).list(queries.capture());
        for (Wrapper query : queries.getAllValues()) {
            QueryWrapper<?> condition = (QueryWrapper<?>) query;
            assertTrue(condition.getSqlSegment().contains("video_id"));
            assertTrue(condition.getSqlSegment().contains("parent_comment_id"));
            assertTrue(condition.getParamNameValuePairs().values().contains(99L));
        }
    }

    private void assertCountUpdate(int count, long videoId) {
        verify(update).setSql("comment_count = comment_count - {0}", count);
        verify(update).eq(any(), eq(videoId));
        verify(update).ge(any(), eq(count));
        verify(update).update();
    }

    private CancelVideoActionRequest request(long id, long videoId) {
        CancelVideoActionRequest request = new CancelVideoActionRequest();
        request.setId(id);
        request.setVideoId(videoId);
        return request;
    }

    private void assertCode(ErrorCode code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code.getCode(), assertThrows(BusinessException.class, action).getCode());
    }
}
