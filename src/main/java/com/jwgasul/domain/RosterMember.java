// RosterMember.java — 명부 구성원(roster_member, 3.6). 생성 시점 인적사항 스냅샷을 보관한다.
// 이후 근로자 수정·삭제·재입사와 무관하게 과거 명부는 스냅샷 값으로 일관되게 재출력된다.
package com.jwgasul.domain;

import com.jwgasul.common.PhoneFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "roster_member")
public class RosterMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "roster_id", nullable = false)
    private Long rosterId;

    @Column(name = "worker_id")
    private Long workerId; // 현재 인원 추적용(표시는 스냅샷 사용)

    @Column(name = "snap_name_ko", nullable = false, length = 50)
    private String snapNameKo;

    @Column(name = "snap_name_foreign", length = 100)
    private String snapNameForeign;

    @Column(name = "snap_phone", nullable = false, length = 20)
    private String snapPhone;

    @Column(name = "snap_birth_date", nullable = false)
    private LocalDate snapBirthDate;

    protected RosterMember() {
    }

    public RosterMember(Long rosterId, Long workerId, String snapNameKo, String snapNameForeign,
                        String snapPhone, LocalDate snapBirthDate) {
        this.rosterId = rosterId;
        this.workerId = workerId;
        this.snapNameKo = snapNameKo;
        this.snapNameForeign = snapNameForeign;
        this.snapPhone = snapPhone;
        this.snapBirthDate = snapBirthDate;
    }

    public Long getId() {
        return id;
    }

    public Long getRosterId() {
        return rosterId;
    }

    public Long getWorkerId() {
        return workerId;
    }

    public String getSnapNameKo() {
        return snapNameKo;
    }

    public String getSnapNameForeign() {
        return snapNameForeign;
    }

    public String getSnapPhone() {
        return snapPhone;
    }

    // 화면 표시용 스냅샷 연락처(010-1234-5678). 엑셀 출력은 원본 스냅샷 값을 쓴다.
    public String getSnapPhoneFormatted() {
        return PhoneFormat.format(snapPhone);
    }

    public LocalDate getSnapBirthDate() {
        return snapBirthDate;
    }
}
