package cn.tolim.pudans.contract.iam.client;

import cn.tolim.pudans.contract.iam.IamContractConstants;
import cn.tolim.pudans.contract.iam.IamContractPaths;
import cn.tolim.pudans.contract.iam.dto.IamApiResponse;
import cn.tolim.pudans.contract.iam.dto.ticket.TicketExchangeRequest;
import cn.tolim.pudans.contract.iam.dto.ticket.TicketExchangeResponse;
import cn.tolim.pudans.contract.iam.dto.ticket.TicketIssueRequest;
import cn.tolim.pudans.contract.iam.dto.ticket.TicketIssueResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

// IAM SSO ticket contract
@FeignClient(name = IamContractConstants.SERVICE_NAME, contextId = "iamTicketClient",
        path = IamContractPaths.BASE)
public interface IamTicketClient {

    // Issue a one-time SSO ticket
    // Parameter request: TicketIssueRequest
    // Returns: IamApiResponse<TicketIssueResponse>
    @PostMapping(IamContractPaths.TICKET_ISSUE_SUFFIX)
    IamApiResponse<TicketIssueResponse> issueTicket(@RequestBody TicketIssueRequest request);

    // Exchange a one-time SSO ticket
    // Parameter request: TicketExchangeRequest
    // Returns: IamApiResponse<TicketExchangeResponse>
    @PostMapping(IamContractPaths.TICKET_EXCHANGE_SUFFIX)
    IamApiResponse<TicketExchangeResponse> exchangeTicket(@RequestBody TicketExchangeRequest request);
}