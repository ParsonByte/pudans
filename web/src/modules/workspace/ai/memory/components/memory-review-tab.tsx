import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";

import { MemoryRecordsPanel } from "./memory-review-records";
import { MemoryRequestsPanel, OrgMemoriesPanel } from "./memory-review-requests";

type MemoryReviewTabProps = {
  currentUserId?: string;
  canReview: boolean;
  canExecute: boolean;
  canDelete: boolean;
  canExport: boolean;
};

export function MemoryReviewTab({
  currentUserId,
  canReview,
  canExecute,
  canDelete,
  canExport,
}: MemoryReviewTabProps) {
  return (
    <Tabs defaultValue="requests" className="gap-3">
      <TabsList variant="line" className="w-full justify-start border-b">
        <TabsTrigger value="requests" className="flex-none">
          修正申请
        </TabsTrigger>
        <TabsTrigger value="org" className="flex-none">
          组织记忆
        </TabsTrigger>
        <TabsTrigger value="records" className="flex-none">
          记忆记录
        </TabsTrigger>
      </TabsList>
      <TabsContent value="requests">
        <MemoryRequestsPanel canReview={canReview} />
      </TabsContent>
      <TabsContent value="org">
        <OrgMemoriesPanel canReview={canReview} />
      </TabsContent>
      <TabsContent value="records">
        <MemoryRecordsPanel
          currentUserId={currentUserId}
          canExecute={canExecute}
          canDelete={canDelete}
          canExport={canExport}
        />
      </TabsContent>
    </Tabs>
  );
}
