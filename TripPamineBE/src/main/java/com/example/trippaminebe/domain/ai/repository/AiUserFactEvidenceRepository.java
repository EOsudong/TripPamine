package com.example.trippaminebe.domain.ai.repository;

import com.example.trippaminebe.domain.ai.entity.AiUserFactEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiUserFactEvidenceRepository
		extends JpaRepository<AiUserFactEvidence, Long> {

	List<AiUserFactEvidence>
	findAllByFact_FactIdOrderByCreatedAtAsc(Long factId);

	boolean existsByFact_FactIdAndEvent_EventId(
			Long factId,
			Long eventId
	);
}