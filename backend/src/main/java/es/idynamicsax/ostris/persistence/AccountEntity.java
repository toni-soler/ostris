package es.idynamicsax.ostris.persistence;

import jakarta.persistence.*;
import java.math.BigInteger;
import java.util.UUID;

@Entity
@Table(name = "account", schema = "ostris")
public class AccountEntity {
    @Id private UUID id;
    @Column(name="tenant_id", nullable=false) private UUID tenantId;
    @Column(name="community_id", nullable=false) private UUID communityId;
    @Column(name="unit_id", nullable=false) private UUID unitId;
    @Column(name="participant_id") private UUID participantId;
    @Column(name="account_type", nullable=false) private String accountType;
    @Column(nullable=false) private String name;
    @Column(name="credit_floor", precision=78, scale=0, nullable=false) private BigInteger creditFloor;
    @Column(name="balance_projection", precision=78, scale=0, nullable=false) private BigInteger balanceProjection;
    @Column(name="risk_state", nullable=false) private String riskState;
    @Version private long version;
    protected AccountEntity() {}
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getCommunityId(){return communityId;}
    public UUID getUnitId(){return unitId;} public String getAccountType(){return accountType;}
    public String getRiskState(){return riskState;}
    public BigInteger getCreditFloor(){return creditFloor;} public BigInteger getBalanceProjection(){return balanceProjection;}
    public void apply(BigInteger amount){balanceProjection=balanceProjection.add(amount);}
}
