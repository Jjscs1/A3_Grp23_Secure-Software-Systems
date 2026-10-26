package com.evp.changerequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** SEC-05 rules on their own, without Spring or a database. */
class DualControlPolicyTest {

    @Test
    void proposerCannotApproveOwnChange() {
        assertThatThrownBy(() -> DualControlPolicy.checkApprover("alice", "alice", List.of()))
                .isInstanceOf(SelfApprovalException.class);
    }

    @Test
    void sameDelegateCannotApproveTwice() {
        assertThatThrownBy(() -> DualControlPolicy.checkApprover("alice", "bob", List.of("bob")))
                .isInstanceOf(DuplicateApprovalException.class);
    }

    @Test
    void differentDelegateMayApprove() {
        assertThatCode(() -> DualControlPolicy.checkApprover("alice", "bob", List.of())).doesNotThrowAnyException();
    }

    @Test
    void recountExclusionNeedsTwoApprovals() {
        assertThat(DualControlPolicy.isSatisfied(ChangeRequestType.RECOUNT_EXCLUSION, 1)).isFalse();
        assertThat(DualControlPolicy.isSatisfied(ChangeRequestType.RECOUNT_EXCLUSION, 2)).isTrue();
    }

    @Test
    void otherChangesNeedOneApproval() {
        assertThat(DualControlPolicy.isSatisfied(ChangeRequestType.CANDIDATE_DETAILS, 0)).isFalse();
        assertThat(DualControlPolicy.isSatisfied(ChangeRequestType.CANDIDATE_DETAILS, 1)).isTrue();
        assertThat(DualControlPolicy.isSatisfied(ChangeRequestType.GROUP_ORDERING, 1)).isTrue();
    }

    @Test
    void recountExclusionNeedsValidCourtOrderReference() {
        assertThatThrownBy(() -> DualControlPolicy.validateProposal(ChangeRequestType.RECOUNT_EXCLUSION, null, null))
                .isInstanceOf(InvalidChangeRequestException.class);
        assertThatThrownBy(() -> DualControlPolicy.validateProposal(ChangeRequestType.RECOUNT_EXCLUSION, "<x>", null))
                .isInstanceOf(InvalidChangeRequestException.class);
        assertThatCode(() -> DualControlPolicy.validateProposal(ChangeRequestType.RECOUNT_EXCLUSION, "CDR 12/2026", null))
                .doesNotThrowAnyException();
    }

    @Test
    void bulkEnrolmentWorkflowIsForMoreThan50Records() {
        assertThatThrownBy(() -> DualControlPolicy.validateProposal(ChangeRequestType.BULK_ENROLMENT_CHANGE, null, 50))
                .isInstanceOf(InvalidChangeRequestException.class);
        assertThatCode(() -> DualControlPolicy.validateProposal(ChangeRequestType.BULK_ENROLMENT_CHANGE, null, 51))
                .doesNotThrowAnyException();
    }
}
